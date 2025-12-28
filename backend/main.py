import cv2
import mediapipe as mp
import numpy as np
from fastapi import FastAPI, UploadFile, File, Form, HTTPException

app = FastAPI(title="AI Gym Posture API")

# Initialize MediaPipe
mp_pose = mp.solutions.pose
mp_drawing = mp.solutions.drawing_utils
pose = mp_pose.Pose(min_detection_confidence=0.5, min_tracking_confidence=0.5)


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
    if angle > 180:
        angle = 360 - angle

    return int(angle)


def get_landmark(landmarks, landmark_index):
    """Get x, y coordinates of a landmark"""
    return [landmarks[landmark_index].x, landmarks[landmark_index].y]


# ------------------ EXERCISE LOGIC ------------------ #


def analyze_biceps_curl(landmarks):
    """Analyze biceps curl form"""
    shoulder = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_SHOULDER.value)
    elbow = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_ELBOW.value)
    wrist = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_WRIST.value)

    angle = calculate_angle(shoulder, elbow, wrist)

    # Determine stage
    if angle > 160:
        stage = "down"
    elif angle < 35:
        stage = "up"
    else:
        stage = "mid"

    # Determine form
    form_status = "GOOD" if 30 <= angle <= 170 else "BAD"

    return angle, stage, form_status


def analyze_deadlift(landmarks):
    """Analyze deadlift form"""
    shoulder = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_SHOULDER.value)
    hip = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_HIP.value)
    knee = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_KNEE.value)

    angle = calculate_angle(shoulder, hip, knee)

    # Determine stage
    if angle > 160:
        stage = "up"
    elif angle < 120:
        stage = "down"
    else:
        stage = "mid"

    # Determine form
    form_status = "GOOD" if 100 <= angle <= 180 else "BAD"

    return angle, stage, form_status


def analyze_triceps_pushdown(landmarks):
    """Analyze triceps pushdown form"""
    shoulder = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_SHOULDER.value)
    elbow = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_ELBOW.value)
    wrist = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_WRIST.value)

    angle = calculate_angle(shoulder, elbow, wrist)

    # Determine stage
    if angle < 60:
        stage = "down"
    elif angle > 140:
        stage = "up"
    else:
        stage = "mid"

    # Determine form
    form_status = "GOOD" if 50 <= angle <= 160 else "BAD"

    return angle, stage, form_status


def analyze_leg_press(landmarks):
    """Analyze leg press form"""
    hip = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_HIP.value)
    knee = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_KNEE.value)
    ankle = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_ANKLE.value)

    angle = calculate_angle(hip, knee, ankle)

    # Determine stage
    if angle > 160:
        stage = "up"
    elif angle < 80:
        stage = "down"
    else:
        stage = "mid"

    # Determine form
    form_status = "GOOD" if 70 <= angle <= 180 else "BAD"

    return angle, stage, form_status


# Map exercise names to functions
EXERCISES = {
    "biceps_curl": analyze_biceps_curl,
    "deadlift": analyze_deadlift,
    "triceps_pushdown": analyze_triceps_pushdown,
    "leg_press": analyze_leg_press,
}


# ------------------ API ROUTES ------------------ #


@app.get("/")
def read_root():
    """API info"""
    return {
        "message": "AI Gym Posture API",
        "available_exercises": list(EXERCISES.keys()),
    }


@app.post("/analyze")
async def analyze_exercise(exercise: str = Form(...), file: UploadFile = File(...)):
    """
    Analyze exercise form from an image

    Parameters:
    - exercise: Exercise type (biceps_curl, deadlift, triceps_pushdown, leg_press)
    - file: Image file containing person performing exercise

    Returns:
    - angle: Joint angle in degrees
    - stage: Exercise stage (up, down, mid)
    - form_status: Form quality (GOOD, BAD)
    """

    # Validate exercise
    if exercise not in EXERCISES:
        raise HTTPException(
            status_code=400,
            detail=f"Invalid exercise. Choose from: {list(EXERCISES.keys())}",
        )

    try:
        # Read image
        image_bytes = await file.read()
        np_img = np.frombuffer(image_bytes, np.uint8)
        frame = cv2.imdecode(np_img, cv2.IMREAD_COLOR)

        if frame is None:
            raise HTTPException(status_code=400, detail="Invalid image file")

        # Convert to RGB for MediaPipe
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = pose.process(rgb)

        # Check if pose detected
        if not results.pose_landmarks:
            return {"success": False, "message": "No person detected in image"}

        # Analyze exercise
        analyze_func = EXERCISES[exercise]
        angle, stage, form_status = analyze_func(results.pose_landmarks.landmark)

        return {
            "success": True,
            "exercise": exercise,
            "angle": angle,
            "stage": stage,
            "form_status": form_status,
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error processing image: {str(e)}")


# ------------------ WEBCAM MODE (CLI) ------------------ #


def run_webcam(exercise_name):
    """Run real-time analysis with webcam"""

    if exercise_name not in EXERCISES:
        print(f"Invalid exercise. Choose from: {list(EXERCISES.keys())}")
        return

    analyze_func = EXERCISES[exercise_name]
    cap = cv2.VideoCapture(0)

    if not cap.isOpened():
        print("Error: Cannot open webcam")
        return

    print(f"\n{'='*50}")
    print(f"Running: {exercise_name.replace('_', ' ').title()}")
    print("Press 'q' to quit")
    print(f"{'='*50}\n")

    with mp_pose.Pose(
        min_detection_confidence=0.5, min_tracking_confidence=0.5
    ) as pose_detector:
        while cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                break

            # Process frame
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = pose_detector.process(rgb)

            if results.pose_landmarks:
                # Analyze exercise
                angle, stage, form_status = analyze_func(
                    results.pose_landmarks.landmark
                )

                # Draw info on frame
                cv2.putText(
                    frame,
                    f"Angle: {angle}",
                    (20, 50),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    1,
                    (255, 255, 255),
                    2,
                )
                cv2.putText(
                    frame,
                    f"Stage: {stage}",
                    (20, 100),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    1,
                    (255, 255, 255),
                    2,
                )
                cv2.putText(
                    frame,
                    f"Form: {form_status}",
                    (20, 150),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    1,
                    (0, 255, 0) if form_status == "GOOD" else (0, 0, 255),
                    2,
                )

                # Draw skeleton
                mp_drawing.draw_landmarks(
                    frame, results.pose_landmarks, mp_pose.POSE_CONNECTIONS
                )
            else:
                cv2.putText(
                    frame,
                    "No pose detected",
                    (20, 50),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    1,
                    (0, 0, 255),
                    2,
                )

            cv2.imshow(exercise_name, frame)

            if cv2.waitKey(10) & 0xFF == ord("q"):
                break

    cap.release()
    cv2.destroyAllWindows()


def main():
    """Command line menu"""
    print("\n" + "=" * 50)
    print("AI GYM POSTURE CORRECTION")
    print("=" * 50)
    print("\nExercises:")
    exercises = list(EXERCISES.keys())
    for i, ex in enumerate(exercises, 1):
        print(f"{i}. {ex.replace('_', ' ').title()}")
    print(f"{len(exercises) + 1}. Exit")
    print("=" * 50)

    choice = input("\nSelect exercise (1-5): ").strip()

    try:
        choice_num = int(choice)
        if 1 <= choice_num <= len(exercises):
            run_webcam(exercises[choice_num - 1])
        elif choice_num == len(exercises) + 1:
            print("Goodbye!")
        else:
            print("Invalid choice")
    except ValueError:
        print("Invalid input")


if __name__ == "__main__":
    main()
