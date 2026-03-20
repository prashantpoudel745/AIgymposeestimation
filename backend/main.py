import cv2
import mediapipe as mp
import numpy as np
import tempfile
import os
import uuid
from fastapi import FastAPI, UploadFile, File, Form, HTTPException, BackgroundTasks
from fastapi.responses import FileResponse, JSONResponse
from typing import Optional
import shutil
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from jose import JWTError, jwt
from passlib.context import CryptContext
from datetime import timedelta
from database import db
from models import UserCreate, UserInDB, UserOut, ExerciseRecord, ExerciseRecordRequest, Token, TokenData, LoginRequest
from auth_utils import verify_password, get_password_hash, create_access_token, ALGORITHM, SECRET_KEY, ACCESS_TOKEN_EXPIRE_MINUTES
from fastapi import Depends

app = FastAPI(title="AI Gym Posture Correction API", version="2.0")

# Initialize MediaPipe (will create per-request instances for thread safety)
mp_pose = mp.solutions.pose
mp_drawing = mp.solutions.drawing_utils
mp_drawing_styles = mp.solutions.drawing_styles

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
    return [lm.x, lm.y] if lm.visibility > 0.5 else None

def cleanup_temp_files(file_paths: list):
    """Safely delete temporary files"""
    for path in file_paths:
        try:
            if os.path.exists(path):
                os.remove(path)
        except Exception as e:
            print(f"Error cleaning up {path}: {str(e)}")

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
        angle = calculate_angle(shoulder, elbow, wrist)
        
        # Count repetitions
        if angle > 160 and self.stage != "down":
            self.stage = "down"
        if angle < 40 and self.stage == "down":
            self.stage = "up"
            self.counter += 1
        
        form_status = "GOOD" if 30 <= angle <= 170 else "BAD"
        if angle < 30:
            self.form_issues = ["Elbow overextended"]
        elif angle > 170:
            self.form_issues = ["Not full contraction"]
        else:
            self.form_issues = []
            
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
        # Draw skeleton with enhanced styling
        mp_drawing.draw_landmarks(
            output_frame,
            results.pose_landmarks,
            mp_pose.POSE_CONNECTIONS,
            landmark_drawing_spec=mp_drawing_styles.get_default_pose_landmarks_style()
        )
        
        # Analyze exercise form
        try:
            angle, stage, form_status = analyzer.analyze(results.pose_landmarks.landmark)
            
            if angle is not None:
                # Draw analysis metrics
                h, w = frame.shape[:2]
                cv2.rectangle(output_frame, (10, 10), (350, 180), (0, 0, 0), -1)
                
                # Color coding for form status
                color = (0, 255, 0) if form_status == "GOOD" else (0, 0, 255)
                
                cv2.putText(output_frame, f"Exercise: {analyzer.exercise_type.replace('_', ' ').title()}", 
                           (20, 40), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
                cv2.putText(output_frame, f"Angle: {angle} deg", 
                           (20, 75), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
                cv2.putText(output_frame, f"Stage: {stage}", 
                           (20, 110), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
                cv2.putText(output_frame, f"Form: {form_status}", 
                           (20, 145), cv2.FONT_HERSHEY_SIMPLEX, 0.8, color, 2)
                
                # Show form issues if any
                if analyzer.form_issues:
                    cv2.putText(output_frame, "ISSUE:", (20, 175), 
                               cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)
            else:
                cv2.putText(output_frame, "Landmark detection failed", 
                           (20, 50), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 255), 2)
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
    
    # Initialize video writer with multiple codec fallbacks
    codecs = [('mp4v', '.mp4'), ('avc1', '.mp4'), ('H264', '.mp4'), ('XVID', '.avi')]
    out = None
    
    for codec_code, ext in codecs:
        try:
            fourcc = cv2.VideoWriter_fourcc(*codec_code)
            out = cv2.VideoWriter(output_path, fourcc, fps, (width, height))
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
    if not os.path.exists(output_path) or os.path.getsize(output_path) < 1000:
        raise HTTPException(500, "Video processing failed - output file invalid")
    
    # Return processing statistics
    return {
        "total_frames": frames_processed,
        "pose_detected_frames": pose_detected_count,
        "detection_rate": round(pose_detected_count / max(frames_processed, 1) * 100, 1),
        "reps_completed": analyzer.counter if hasattr(analyzer, 'counter') else 0
    }


# ------------------ AUTHENTICATION ------------------ #

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="login")

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
async def login(form_data: OAuth2PasswordRequestForm = Depends()):
    user = await db.users.find_one({"email": form_data.username})
    if not user or not verify_password(form_data.password, user["hashed_password"]):
        raise HTTPException(status_code=401, detail="Incorrect email or password")
    
    access_token_expires = timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    access_token = create_access_token(
        data={"sub": user["email"]}, expires_delta=access_token_expires
    )
    return {"access_token": access_token, "token_type": "bearer"}


# ------------------ AUTHENTICATION ------------------ #

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="login")

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
async def login(form_data: LoginRequest):
    user = await db.users.find_one({"email": form_data.email})
    if not user or not verify_password(form_data.password, user["hashed_password"]):
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
        
        # Save record to database
        record = ExerciseRecord(
            user_id=str(current_user["_id"]),
            exercise_type=exercise,
            side=side,
            reps=stats.get("reps_completed", 0),
            detection_rate=stats["detection_rate"]
        )
        await db.exercise_records.insert_one(record.dict())
        
        # Verify output exists
        if not os.path.exists(output_path):
            raise HTTPException(500, "Video processing failed - output file not created")
        
        # Return processed video with statistics in headers
        response = FileResponse(
            output_path,
            media_type="video/mp4",
            filename=f"analyzed_{exercise}_{side}.mp4",
            headers={
                "X-Total-Frames": str(stats["total_frames"]),
                "X-Pose-Detection-Rate": f"{stats['detection_rate']}%",
                "X-Reps-Completed": str(stats.get("reps_completed", 0)),
                "X-Exercise-Type": exercise,
                "X-Analyzed-Side": side
            }
        )
        return response
    
    except HTTPException:
        raise
    except Exception as e:
        # Ensure cleanup on error
        cleanup_temp_files([input_path, output_path])
        raise HTTPException(500, f"Video processing failed: {str(e)}")


@app.post("/exercise-record")
async def save_exercise_record(
    record: ExerciseRecordRequest,
    current_user: dict = Depends(get_current_user)
):
    """Save exercise session record from the app"""
    record_dict = record.dict()
    record_dict["user_id"] = str(current_user["_id"])
    await db.exercise_records.insert_one(record_dict)
    return {"success": True, "message": "Record saved successfully"}


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