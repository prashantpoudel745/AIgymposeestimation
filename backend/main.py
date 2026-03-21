import cv2
import mediapipe as mp
import numpy as np
import tempfile
import os
import uuid
import cloudinary
import cloudinary.uploader
import cloudinary.utils
from cloudinary.exceptions import Error as CloudinaryError
from fastapi import FastAPI, UploadFile, File, Form, HTTPException, BackgroundTasks, Request
from fastapi.responses import FileResponse, JSONResponse
from typing import Optional
import shutil
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from jose import JWTError, jwt
from passlib.context import CryptContext
from datetime import timedelta, datetime

try:
    from .database import db
    from .models import UserCreate, UserInDB, UserOut, ExerciseRecord, ExerciseRecordRequest, Token, TokenData, LoginRequest
    from .auth_utils import verify_password, get_password_hash, create_access_token, ALGORITHM, SECRET_KEY, ACCESS_TOKEN_EXPIRE_MINUTES
except ImportError:
    from database import db
    from models import UserCreate, UserInDB, UserOut, ExerciseRecord, ExerciseRecordRequest, Token, TokenData, LoginRequest
    from auth_utils import verify_password, get_password_hash, create_access_token, ALGORITHM, SECRET_KEY, ACCESS_TOKEN_EXPIRE_MINUTES
from fastapi import Depends

app = FastAPI(title="AI Gym Posture Correction API", version="2.0")

# Initialize MediaPipe (will create per-request instances for thread safety)
mp_pose = mp.solutions.pose
mp_drawing = mp.solutions.drawing_utils
mp_drawing_styles = mp.solutions.drawing_styles

# A strict 0.5 cutoff can drop valid landmarks in moving/angled videos.
LANDMARK_VISIBILITY_THRESHOLD = float(os.getenv("LANDMARK_VISIBILITY_THRESHOLD", "0.35"))

CLOUDINARY_CLOUD_NAME = os.getenv("CLOUDINARY_CLOUD_NAME")
CLOUDINARY_API_KEY = os.getenv("CLOUDINARY_API_KEY")
CLOUDINARY_API_SECRET = os.getenv("CLOUDINARY_API_SECRET")

if CLOUDINARY_CLOUD_NAME and CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET:
    cloudinary.config(
        cloud_name=CLOUDINARY_CLOUD_NAME,
        api_key=CLOUDINARY_API_KEY,
        api_secret=CLOUDINARY_API_SECRET,
        secure=True,
    )

# ------------------ UTILITY FUNCTIONS ------------------ #

def calculate_angle(a, b, c):
    """Calculate angle between three points"""
    a = np.array(a)
    b = np.array(b)
    c = np.array(c)

    radians = np.arctan2(c[1] - b[1], c[0] - b[0]) - np.arctan2(
        a[1] - b[1], a[0] - b[0]
    )
    angle = np.abs(radians * 180.0 / np.pi)
    return int(360 - angle if angle > 180 else angle)

def get_landmark(landmarks, landmark_index):
    """Safely get x, y coordinates of a landmark"""
    if landmark_index >= len(landmarks):
        raise ValueError(f"Landmark index {landmark_index} out of range")
    lm = landmarks[landmark_index]
    visibility = getattr(lm, "visibility", 1.0)
    return [lm.x, lm.y] if visibility >= LANDMARK_VISIBILITY_THRESHOLD else None

def cleanup_temp_files(file_paths: list):
    """Safely delete temporary files"""
    for path in file_paths:
        try:
            if os.path.exists(path):
                os.remove(path)
        except Exception as e:
            print(f"Error cleaning up {path}: {str(e)}")


def upload_video_to_cloudinary(video_path: str, user_id: str, exercise: str, side: str, variant: str):
    """Upload video to Cloudinary and return a browser-playable MP4 URL + public ID."""
    if not (CLOUDINARY_CLOUD_NAME and CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET):
        raise HTTPException(
            status_code=500,
            detail="Cloudinary is not configured. Set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, and CLOUDINARY_API_SECRET.",
        )

    public_id = f"aigym/{user_id}/{exercise}_{side}_{variant}_{uuid.uuid4().hex}"

    try:
        upload_result = cloudinary.uploader.upload_large(
            video_path,
            resource_type="video",
            public_id=public_id,
            overwrite=True,
            eager=[{"format": "mp4"}],
            eager_async=False,
        )
        uploaded_public_id = upload_result.get("public_id")
        eager_items = upload_result.get("eager") or []

        # Prefer eagerly generated mp4 URL because it is reliably playable in browser/clients.
        playable_url = eager_items[0].get("secure_url") if eager_items else None
        if not playable_url and uploaded_public_id:
            playable_url = cloudinary.utils.cloudinary_url(
                uploaded_public_id,
                resource_type="video",
                format="mp4",
                secure=True,
            )[0]

        if not playable_url:
            raise HTTPException(status_code=500, detail="Cloudinary upload succeeded but did not return video URL")

        return playable_url, uploaded_public_id
    except CloudinaryError as exc:
        raise HTTPException(status_code=502, detail=f"Cloudinary upload failed: {str(exc)}")

# ------------------ EXERCISE ANALYSIS ------------------ #

class PostureAnalyzer:
    """Handles exercise-specific analysis with side selection"""
    
    def __init__(self, exercise_type: str, side: str = "left"):
        self.exercise_type = exercise_type
        self.side = side.lower()
        self.counter = 0
        self.stage = None
        self.form_issues = []
        
        # Map sides to landmark indices
        self.side_map = {
            "left": {
                "shoulder": mp_pose.PoseLandmark.LEFT_SHOULDER,
                "elbow": mp_pose.PoseLandmark.LEFT_ELBOW,
                "wrist": mp_pose.PoseLandmark.LEFT_WRIST,
                "hip": mp_pose.PoseLandmark.LEFT_HIP,
                "knee": mp_pose.PoseLandmark.LEFT_KNEE,
                "ankle": mp_pose.PoseLandmark.LEFT_ANKLE
            },
            "right": {
                "shoulder": mp_pose.PoseLandmark.RIGHT_SHOULDER,
                "elbow": mp_pose.PoseLandmark.RIGHT_ELBOW,
                "wrist": mp_pose.PoseLandmark.RIGHT_WRIST,
                "hip": mp_pose.PoseLandmark.RIGHT_HIP,
                "knee": mp_pose.PoseLandmark.RIGHT_KNEE,
                "ankle": mp_pose.PoseLandmark.RIGHT_ANKLE
            }
        }
    
    def _get_landmarks(self, landmarks, *keys):
        """Get multiple landmarks for current side"""
        lm_set = self.side_map[self.side]
        result = []
        for key in keys:
            idx = lm_set[key].value
            lm = get_landmark(landmarks, idx)
            if lm is None:
                raise ValueError(f"Landmark {key} not visible for {self.side} side")
            result.append(lm)
        return result
    
    def analyze(self, landmarks):
        """Analyze current frame based on exercise type"""
        try:
            if self.exercise_type == "biceps_curl":
                return self._analyze_biceps_curl(landmarks)
            elif self.exercise_type == "deadlift":
                return self._analyze_deadlift(landmarks)
            elif self.exercise_type == "triceps_pushdown":
                return self._analyze_triceps_pushdown(landmarks)
            elif self.exercise_type == "leg_press":
                return self._analyze_leg_press(landmarks)
            else:
                raise ValueError(f"Unsupported exercise: {self.exercise_type}")
        except ValueError as e:
            return None, "undetected", f"LANDMARK_ERROR: {str(e)}"
    
    def _analyze_biceps_curl(self, landmarks):
        shoulder, elbow, wrist = self._get_landmarks(landmarks, "shoulder", "elbow", "wrist")
        try:
            hip = self._get_landmarks(landmarks, "hip")[0]
        except ValueError:
            hip = None

        angle = calculate_angle(shoulder, elbow, wrist)

        # Match frontend stage machine: down when extended (>130), up at peak (<50).
        if angle > 130 and self.stage != "down":
            self.stage = "down"

        # Frontend transition rule: once descending from top, move out of "up" state.
        if angle > 80 and self.stage == "up":
            self.stage = "mid"

        if angle < 50 and self.stage == "down":
            self.stage = "up"
            self.counter += 1

        self.form_issues = []

        # 1) Lockout warning at bottom.
        if self.stage == "down" and angle > 160:
            self.form_issues.append("Don't lock out your elbow at the bottom")

        # 2) Elbow tuck check: shoulder->elbow line should stay near vertical.
        dx = elbow[0] - shoulder[0]
        dy = elbow[1] - shoulder[1]
        upper_arm_angle_deg = float(np.degrees(np.arctan2(abs(dx), abs(dy))))
        if upper_arm_angle_deg > 25.0:
            self.form_issues.append("Keep your elbow tucked - upper arm is swinging")

        # 3) Incomplete peak contraction and wrist drop checks at top position.
        if self.stage == "up" and angle > 70:
            self.form_issues.append("Curl higher - incomplete contraction at peak")
        if self.stage == "up" and wrist[1] > elbow[1]:
            self.form_issues.append("Keep your wrist above elbow level at the top")

        # 4) Torso sway check when hip landmark is visible.
        if hip is not None:
            torso_dx = shoulder[0] - hip[0]
            torso_dy = shoulder[1] - hip[1]
            torso_angle_deg = float(np.degrees(np.arctan2(abs(torso_dx), abs(torso_dy))))
            if torso_angle_deg > 15.0:
                self.form_issues.append("Stand upright - avoid swinging your torso")

        form_status = "GOOD" if not self.form_issues else "BAD"
            
        return angle, self.stage or "mid", form_status
    
    def _analyze_deadlift(self, landmarks):
        shoulder, hip, knee = self._get_landmarks(landmarks, "shoulder", "hip", "knee")
        angle = calculate_angle(shoulder, hip, knee)
        
        form_status = "GOOD" if 100 <= angle <= 180 else "BAD"
        self.form_issues = []
        if angle < 100:
            self.form_issues = ["Back rounding - risk of injury!"]
        elif angle > 170:
            self.form_issues = ["Insufficient hip hinge"]
            
        return angle, "mid", form_status
    
    def _analyze_triceps_pushdown(self, landmarks):
        shoulder, elbow, wrist = self._get_landmarks(landmarks, "shoulder", "elbow", "wrist")
        angle = calculate_angle(shoulder, elbow, wrist)
        
        form_status = "GOOD" if 50 <= angle <= 160 else "BAD"
        self.form_issues = []
        if angle < 50:
            self.form_issues = ["Elbow flaring"]
        elif angle > 160:
            self.form_issues = ["Incomplete extension"]
            
        return angle, "mid", form_status
    
    def _analyze_leg_press(self, landmarks):
        hip, knee, ankle = self._get_landmarks(landmarks, "hip", "knee", "ankle")
        angle = calculate_angle(hip, knee, ankle)
        
        form_status = "GOOD" if 70 <= angle <= 180 else "BAD"
        self.form_issues = []
        if angle < 70:
            self.form_issues = ["Knee overextension - injury risk!"]
        elif angle > 170:
            self.form_issues = ["Incomplete range of motion"]
            
        return angle, "mid", form_status


# ------------------ VIDEO PROCESSING ------------------ #

def process_video_frame(frame, pose_detector, analyzer):
    """Process a single video frame with pose estimation and analysis"""
    # Convert to RGB for MediaPipe
    rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    results = pose_detector.process(rgb)
    
    # Prepare output frame
    output_frame = frame.copy()
    
    if results.pose_landmarks:
        # Frontend-like overlay palette (BGR for OpenCV)
        skeleton_line_color = (134, 135, 1)   # teal_700-ish
        skeleton_point_color = (0, 255, 255)  # yellow
        info_color = (185, 128, 41)           # #2980B9
        good_color = (96, 174, 39)            # #27AE60
        warn_color = (34, 126, 230)           # #E67E22
        bad_form_color = (60, 76, 231)        # #E74C3C

        # Draw skeleton with frontend-like styling
        mp_drawing.draw_landmarks(
            output_frame,
            results.pose_landmarks,
            mp_pose.POSE_CONNECTIONS,
            landmark_drawing_spec=mp_drawing.DrawingSpec(
                color=skeleton_point_color,
                thickness=2,
                circle_radius=2,
            ),
            connection_drawing_spec=mp_drawing.DrawingSpec(
                color=skeleton_line_color,
                thickness=2,
                circle_radius=2,
            ),
        )
        
        # Analyze exercise form
        try:
            angle, stage, form_status = analyzer.analyze(results.pose_landmarks.landmark)
            
            if angle is not None:
                # Draw analysis metrics
                overlay = output_frame.copy()
                cv2.rectangle(overlay, (10, 10), (430, 190), (0, 0, 0), -1)
                cv2.addWeighted(overlay, 0.58, output_frame, 0.42, 0, output_frame)
                
                # Color coding for form status
                form_color = good_color if form_status == "GOOD" else bad_form_color
                
                cv2.putText(output_frame, f"Exercise: {analyzer.exercise_type.replace('_', ' ').title()}", 
                           (20, 40), cv2.FONT_HERSHEY_SIMPLEX, 0.62, info_color, 2)
                cv2.putText(output_frame, f"Angle: {angle} deg", 
                           (20, 75), cv2.FONT_HERSHEY_SIMPLEX, 0.62, info_color, 2)
                cv2.putText(output_frame, f"Stage: {stage}", 
                           (20, 110), cv2.FONT_HERSHEY_SIMPLEX, 0.62, info_color, 2)
                cv2.putText(output_frame, f"Form: {form_status}", 
                           (20, 145), cv2.FONT_HERSHEY_SIMPLEX, 0.75, form_color, 2)
                
                # Show form issues if any
                if analyzer.form_issues:
                    issue_text = analyzer.form_issues[0][:45]
                    cv2.putText(output_frame, f"Warning: {issue_text}", (20, 175),
                               cv2.FONT_HERSHEY_SIMPLEX, 0.55, warn_color, 2)
            else:
                error_text = form_status if isinstance(form_status, str) and form_status.startswith("LANDMARK_ERROR") else "Landmark detection failed"
                cv2.putText(output_frame, error_text[:70],
                           (20, 50), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)
        except Exception as e:
            cv2.putText(output_frame, f"Analysis error: {str(e)[:30]}", 
                       (20, 50), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 165, 255), 2)
    else:
        # No pose detected
        cv2.rectangle(output_frame, (10, 10), (400, 60), (0, 0, 0), -1)
        cv2.putText(output_frame, "NO POSE DETECTED", 
                   (20, 45), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 0, 255), 2)
    
    return output_frame, results.pose_landmarks is not None


def process_video_file(input_path: str, output_path: str, exercise: str, side: str = "left"):
    """Process entire video file frame-by-frame"""
    cap = cv2.VideoCapture(input_path)
    if not cap.isOpened():
        raise HTTPException(400, "Cannot open video file - unsupported codec or corrupted file")
    
    # Get video properties
    fps = int(cap.get(cv2.CAP_PROP_FPS))
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    
    # Fallback FPS if invalid
    if fps <= 0 or fps > 120:
        fps = 30
    
    # Keep output in MP4 container only to avoid unplayable codec/container mismatches.
    codecs = [('mp4v', '.mp4'), ('avc1', '.mp4'), ('H264', '.mp4')]
    out = None
    selected_output_path = output_path
    
    for codec_code, ext in codecs:
        try:
            selected_output_path = f"{os.path.splitext(output_path)[0]}{ext}"
            fourcc = cv2.VideoWriter_fourcc(*codec_code)
            out = cv2.VideoWriter(selected_output_path, fourcc, fps, (width, height))
            if out.isOpened():
                break
            out.release()
        except:
            continue
    
    if out is None or not out.isOpened():
        cap.release()
        raise HTTPException(500, "Could not initialize video writer - codec issue")
    
    # Initialize analyzer and pose detector
    analyzer = PostureAnalyzer(exercise, side)
    
    frames_processed = 0
    pose_detected_count = 0
    
    with mp_pose.Pose(
        static_image_mode=False,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5
    ) as pose_detector:
        while cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                break

            # Match frontend mirrored camera behavior for consistent pose side mapping.
            frame = cv2.flip(frame, 1)
            
            # Process frame
            processed_frame, pose_detected = process_video_frame(frame, pose_detector, analyzer)
            out.write(processed_frame)
            
            # Statistics
            frames_processed += 1
            if pose_detected:
                pose_detected_count += 1
            
            # Progress indicator (optional for debugging)
            if frames_processed % 100 == 0:
                print(f"Processed {frames_processed}/{total_frames} frames")
    
    # Release resources
    cap.release()
    out.release()
    
    # Verify output file was created successfully
    if not os.path.exists(selected_output_path) or os.path.getsize(selected_output_path) < 1000:
        raise HTTPException(500, "Video processing failed - output file invalid")
    
    # Return processing statistics
    return {
        "total_frames": frames_processed,
        "pose_detected_frames": pose_detected_count,
        "detection_rate": round(pose_detected_count / max(frames_processed, 1) * 100, 1),
        "reps_completed": analyzer.counter if hasattr(analyzer, 'counter') else 0,
        "processed_output_path": selected_output_path,
    }


# ------------------ AUTHENTICATION ------------------ #

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/token")

async def get_current_user(token: str = Depends(oauth2_scheme)):
    credentials_exception = HTTPException(
        status_code=401,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        email: str = payload.get("sub")
        if email is None:
            raise credentials_exception
        token_data = TokenData(email=email)
    except JWTError:
        raise credentials_exception
    
    user = await db.users.find_one({"email": token_data.email})
    if user is None:
        raise credentials_exception
    return user

@app.post("/register", response_model=UserOut)
async def register(user: UserCreate):
    existing_user = await db.users.find_one({"email": user.email})
    if existing_user:
        raise HTTPException(status_code=400, detail="Email already registered")
    
    hashed_password = get_password_hash(user.password)
    user_in_db = UserInDB(**user.dict(), hashed_password=hashed_password)
    
    new_user = await db.users.insert_one(user_in_db.dict())
    created_user = await db.users.find_one({"_id": new_user.inserted_id})
    return created_user

@app.post("/login", response_model=Token)
async def login(request: Request):
    content_type = request.headers.get("content-type", "").lower()

    email = None
    password = None

    if "application/json" in content_type:
        payload = await request.json()
        email = payload.get("email")
        password = payload.get("password")
    else:
        form_data = await request.form()
        email = form_data.get("email") or form_data.get("username")
        password = form_data.get("password")

    if not email or not password:
        raise HTTPException(status_code=422, detail="Email/username and password are required")

    user = await db.users.find_one({"email": email})
    stored_hash = user.get("hashed_password") if user else None
    if not user or not verify_password(password, stored_hash):
        raise HTTPException(status_code=401, detail="Incorrect email or password")
    
    access_token_expires = timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    access_token = create_access_token(
        data={"sub": user["email"]}, expires_delta=access_token_expires
    )
    return {"access_token": access_token, "token_type": "bearer"}


# ------------------ AUTHENTICATION ------------------ #

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/token")

async def get_current_user(token: str = Depends(oauth2_scheme)):
    credentials_exception = HTTPException(
        status_code=401,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        email: str = payload.get("sub")
        if email is None:
            raise credentials_exception
        token_data = TokenData(email=email)
    except JWTError:
        raise credentials_exception
    
    user = await db.users.find_one({"email": token_data.email})
    if user is None:
        raise credentials_exception
    return user

@app.post("/register", response_model=UserOut)
async def register(user: UserCreate):
    existing_user = await db.users.find_one({"email": user.email})
    if existing_user:
        raise HTTPException(status_code=400, detail="Email already registered")
    
    hashed_password = get_password_hash(user.password)
    user_in_db = UserInDB(**user.dict(), hashed_password=hashed_password)
    
    new_user = await db.users.insert_one(user_in_db.dict())
    created_user = await db.users.find_one({"_id": new_user.inserted_id})
    return created_user

@app.post("/login", response_model=Token)
async def login(request: Request):
    content_type = request.headers.get("content-type", "").lower()

    email = None
    password = None

    if "application/json" in content_type:
        payload = await request.json()
        email = payload.get("email")
        password = payload.get("password")
    else:
        form_data = await request.form()
        email = form_data.get("email") or form_data.get("username")
        password = form_data.get("password")

    if not email or not password:
        raise HTTPException(status_code=422, detail="Email/username and password are required")

    user = await db.users.find_one({"email": email})
    stored_hash = user.get("hashed_password") if user else None
    if not user or not verify_password(password, stored_hash):
        raise HTTPException(status_code=401, detail="Incorrect email or password")
    
    access_token_expires = timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    access_token = create_access_token(
        data={"sub": user["email"]}, expires_delta=access_token_expires
    )
    return {"access_token": access_token, "token_type": "bearer"}


@app.post("/token", response_model=Token)
async def token_login(form_data: OAuth2PasswordRequestForm = Depends()):
    """OAuth2-compatible login endpoint used by Swagger Authorize and OAuth clients."""
    email = form_data.username
    password = form_data.password

    user = await db.users.find_one({"email": email})
    stored_hash = user.get("hashed_password") if user else None
    if not user or not verify_password(password, stored_hash):
        raise HTTPException(status_code=401, detail="Incorrect email or password")

    access_token_expires = timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    access_token = create_access_token(
        data={"sub": user["email"]}, expires_delta=access_token_expires
    )
    return {"access_token": access_token, "token_type": "bearer"}


# ------------------ API ENDPOINTS ------------------ #

@app.get("/")
def read_root():
    """API information and capabilities"""
    return {
        "name": "AI Gym Posture Correction API",
        "version": "2.0",
        "endpoints": {
            "GET /": "This information",
            "POST /analyze-image": "Analyze single image (returns JSON metrics)",
            "POST /analyze-video": "Analyze video file (returns processed video with overlays)",
            "GET /exercises": "List supported exercises and configurations"
        },
        "supported_exercises": ["biceps_curl", "deadlift", "triceps_pushdown", "leg_press"],
        "supported_sides": ["left", "right"]
    }

@app.get("/exercises")
def list_exercises():
    """Detailed exercise capabilities"""
    return {
        "exercises": {
            "biceps_curl": {
                "description": "Bicep curl form analysis",
                "metrics": ["elbow angle", "repetition count", "full range of motion"],
                "form_checks": ["elbow position", "back stability"]
            },
            "deadlift": {
                "description": "Deadlift form analysis focusing on hip hinge",
                "metrics": ["hip angle", "spinal alignment"],
                "form_checks": ["back rounding", "knee position"]
            },
            "triceps_pushdown": {
                "description": "Triceps pushdown form analysis",
                "metrics": ["elbow angle", "shoulder stability"],
                "form_checks": ["elbow flaring", "body movement"]
            },
            "leg_press": {
                "description": "Leg press form analysis",
                "metrics": ["knee angle", "range of motion"],
                "form_checks": ["knee overextension", "heel position"]
            }
        },
        "analysis_features": [
            "Real-time skeleton overlay",
            "Joint angle measurement",
            "Form quality assessment (GOOD/BAD)",
            "Specific form issue detection",
            "Repetition counting (for applicable exercises)",
            "Side selection (left/right)"
        ]
    }

@app.post("/analyze-image")
async def analyze_image(
    exercise: str = Form(..., description="Exercise type"),
    side: str = Form("left", description="Body side to analyze (left/right)"),
    file: UploadFile = File(...),
    current_user: dict = Depends(get_current_user)
):
    """Analyze single image and return posture metrics"""
    # Validate inputs
    if exercise not in ["biceps_curl", "deadlift", "triceps_pushdown", "leg_press"]:
        raise HTTPException(400, f"Invalid exercise. Supported: biceps_curl, deadlift, triceps_pushdown, leg_press")
    
    if side.lower() not in ["left", "right"]:
        raise HTTPException(400, "Invalid side. Must be 'left' or 'right'")
    
    # Read and validate image
    try:
        contents = await file.read()
        np_img = np.frombuffer(contents, np.uint8)
        frame = cv2.imdecode(np_img, cv2.IMREAD_COLOR)
        
        if frame is None or frame.size == 0:
            raise HTTPException(400, "Invalid image file - could not decode")
        
        # Process image
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        
        with mp_pose.Pose(
            static_image_mode=True,
            min_detection_confidence=0.5
        ) as pose_detector:
            results = pose_detector.process(rgb)
            
            if not results.pose_landmarks:
                return JSONResponse({
                    "success": False,
                    "message": "No person detected in image",
                    "form_status": "UNDETECTED"
                }, status_code=422)
            
            # Analyze exercise
            analyzer = PostureAnalyzer(exercise, side)
            angle, stage, form_status = analyzer.analyze(results.pose_landmarks.landmark)
            
            return {
                "success": True,
                "exercise": exercise,
                "side": side,
                "angle": angle,
                "stage": stage,
                "form_status": form_status,
                "form_issues": analyzer.form_issues,
                "landmark_confidence": round(np.mean([lm.visibility for lm in results.pose_landmarks.landmark]), 3)
            }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(500, f"Image processing failed: {str(e)}")

@app.post("/analyze-video")
async def analyze_video(
    background_tasks: BackgroundTasks,
    exercise: str = Form(..., description="Exercise type"),
    side: str = Form("left", description="Body side to analyze (left/right)"),
    file: UploadFile = File(...),
    current_user: dict = Depends(get_current_user)
):
    """
    Analyze exercise form from a video file and return processed video with overlays
    
    Returns:
    - Processed video file with skeleton overlay and real-time metrics
    - Processing statistics in headers (X-Total-Frames, X-Pose-Detection-Rate, etc.)
    """
    # Validate inputs
    if exercise not in ["biceps_curl", "deadlift", "triceps_pushdown", "leg_press"]:
        raise HTTPException(400, f"Invalid exercise. Supported: biceps_curl, deadlift, triceps_pushdown, leg_press")
    
    if side.lower() not in ["left", "right"]:
        raise HTTPException(400, "Invalid side. Must be 'left' or 'right'")
    
    # Validate file type
    if not file.content_type.startswith('video/'):
        raise HTTPException(400, "File must be a video (MP4, AVI, MOV, etc.)")
    
    # Create temporary files
    input_path = os.path.join(tempfile.gettempdir(), f"input_{uuid.uuid4().hex}{os.path.splitext(file.filename)[1]}")
    output_path = os.path.join(tempfile.gettempdir(), f"output_{uuid.uuid4().hex}.mp4")
    
    # Cleanup files after response
    background_tasks.add_task(cleanup_temp_files, [input_path, output_path])
    
    try:
        # Save uploaded file
        with open(input_path, "wb") as f:
            shutil.copyfileobj(file.file, f)
        
        # Process video
        stats = process_video_file(input_path, output_path, exercise, side)

        # Upload original user video from FE to Cloudinary.
        original_video_url, original_cloudinary_public_id = upload_video_to_cloudinary(
            input_path,
            str(current_user["_id"]),
            exercise,
            side,
            "original",
        )

        processed_output_path = stats.get("processed_output_path", output_path)
        if os.path.abspath(processed_output_path) == os.path.abspath(input_path):
            raise HTTPException(status_code=500, detail="Processed output path resolved to raw input path")

        # Upload processed (annotated) video to Cloudinary.
        processed_video_url, processed_cloudinary_public_id = upload_video_to_cloudinary(
            processed_output_path,
            str(current_user["_id"]),
            exercise,
            side,
            "processed",
        )
        
        # Save record to database
        record = ExerciseRecord(
            user_id=str(current_user["_id"]),
            exercise_type=exercise,
            side=side,
            reps=stats.get("reps_completed", 0),
            detection_rate=stats["detection_rate"],
            video_url=processed_video_url,
            processed_video_url=processed_video_url,
            raw_video_url=original_video_url,
            original_video_url=original_video_url,
            cloudinary_public_id=processed_cloudinary_public_id,
            processed_cloudinary_public_id=processed_cloudinary_public_id,
            raw_cloudinary_public_id=original_cloudinary_public_id,
            original_cloudinary_public_id=original_cloudinary_public_id,
            raw_video_size_bytes=os.path.getsize(input_path),
            processed_video_size_bytes=os.path.getsize(processed_output_path),
        )
        await db.exercise_records.insert_one(record.dict())
        
        # Verify output exists
        if not os.path.exists(processed_output_path):
            raise HTTPException(500, "Video processing failed - output file not created")
        
        # Return processed video with statistics in headers
        response = FileResponse(
    processed_output_path,
    media_type="video/mp4",
    filename=f"analyzed_{exercise}_{side}.mp4",
    headers={
        "X-Total-Frames": str(stats["total_frames"]),
        "X-Pose-Detection-Rate": f"{stats['detection_rate']}%",
        "X-Reps-Completed": str(stats.get("reps_completed", 0)),
        "X-Exercise-Type": exercise,
        "X-Analyzed-Side": side,

        # ✅ Clearly separated URLs
        "X-Cloudinary-Processed-Video-Url": processed_video_url,   # mediapipe annotated
        "X-Cloudinary-Original-Video-Url": original_video_url,     # raw upload

        # ✅ Clearly separated sizes
        "X-Raw-Video-Size": str(os.path.getsize(input_path)),
        "X-Processed-Video-Size": str(os.path.getsize(processed_output_path)),
    }
)
        return response
    
    except HTTPException:
        raise
    except Exception as e:
        # Ensure cleanup on error
        cleanup_temp_files([input_path, output_path])
        raise HTTPException(500, f"Video processing failed: {str(e)}")

@app.get('/fetch-history')
async def fetch_history(
    exercise_type: Optional[str] = None,
    side: Optional[str] = None,
    current_user: dict = Depends(get_current_user)
):
    """Fetch exercise history for the authenticated user with optional filters"""
    query = {"user_id": str(current_user["_id"])}
    
    if exercise_type:
        query["exercise_type"] = exercise_type
    if side:
        query["side"] = side
    
    records_cursor = db.exercise_records.find(query).sort("timestamp", -1)
    records = []
    async for record in records_cursor:
        record["_id"] = str(record["_id"])
        if "timestamp" in record and hasattr(record["timestamp"], "isoformat"):
            record["timestamp"] = record["timestamp"].isoformat()
        records.append(record)
    
    return {"success": True, "records": records}

# ------------------ CLI WEBCAM MODE (Enhanced) ------------------ #

def run_webcam(exercise_name: str, side: str = "left"):
    """Run real-time analysis with webcam with enhanced UI"""
    if exercise_name not in ["biceps_curl", "deadlift", "triceps_pushdown", "leg_press"]:
        print(f"Invalid exercise. Choose from: biceps_curl, deadlift, triceps_pushdown, leg_press")
        return

    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("Error: Cannot open webcam")
        return

    analyzer = PostureAnalyzer(exercise_name, side)
    print(f"\n{'='*60}")
    print(f"Running: {exercise_name.replace('_', ' ').title()} ({side.capitalize()} side)")
    print("Controls: 'q' to quit | 'r' to reset counter")
    print(f"{'='*60}\n")

    with mp_pose.Pose(
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5
    ) as pose_detector:
        while cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                break

            # Process frame
            processed_frame, _ = process_video_frame(frame, pose_detector, analyzer)
            # Get frame dimensions
            height, width, _ = processed_frame.shape
            
            # Add counter display for applicable exercises
            if exercise_name == "biceps_curl":
                cv2.rectangle(processed_frame, (width-200, 10), (width-20, 80), (0, 0, 0), -1)
                cv2.putText(processed_frame, f"REPS: {analyzer.counter}", 
                           (width-190, 60), cv2.FONT_HERSHEY_SIMPLEX, 1.2, (255, 255, 0), 3)
            
            cv2.imshow('AI Gym Posture Correction', processed_frame)
            key = cv2.waitKey(10) & 0xFF
            
            if key == ord('q'):
                break
            elif key == ord('r') and exercise_name == "biceps_curl":
                analyzer.counter = 0
                analyzer.stage = None

    cap.release()
    cv2.destroyAllWindows()

def main():
    """Enhanced command line interface"""
    print("\n" + "=" * 60)
    print("AI GYM POSTURE CORRECTION SYSTEM")
    print("=" * 60)
    print("\nSupported Exercises:")
    exercises = ["biceps_curl", "deadlift", "triceps_pushdown", "leg_press"]
    for i, ex in enumerate(exercises, 1):
        print(f"  {i}. {ex.replace('_', ' ').title()}")
    print("\nOptions:")
    print("  5. Exit")
    print("=" * 60)

    try:
        choice = int(input("\nSelect exercise (1-5): ").strip())
        
        if choice == 5:
            print("Goodbye!")
            return
        elif 1 <= choice <= 4:
            exercise = exercises[choice-1]
            side = input("Analyze left or right side? [left/right] (default: left): ").strip().lower()
            side = side if side in ["left", "right"] else "left"
            run_webcam(exercise, side)
        else:
            print("Invalid choice. Please select 1-5.")
    except ValueError:
        print("Invalid input. Please enter a number.")
    except KeyboardInterrupt:
        print("\nExiting gracefully...")
    finally:
        cv2.destroyAllWindows()

if __name__ == "__main__":
    main()