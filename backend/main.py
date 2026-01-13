import cv2
import mediapipe as mp
import numpy as np
import tempfile
import os

from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.responses import FileResponse

# ===================== APP =====================
app = FastAPI(title="AI Gym Posture Video API")

# ===================== MEDIAPIPE =====================
mp_pose = mp.solutions.pose
mp_drawing = mp.solutions.drawing_utils

pose = mp_pose.Pose(min_detection_confidence=0.5, min_tracking_confidence=0.5)


# ===================== UTILITIES =====================
def calculate_angle(a, b, c):
    a, b, c = np.array(a), np.array(b), np.array(c)
    radians = np.arctan2(c[1] - b[1], c[0] - b[0]) - np.arctan2(
        a[1] - b[1], a[0] - b[0]
    )
    angle = abs(radians * 180.0 / np.pi)
    return int(360 - angle if angle > 180 else angle)


def get_landmark(landmarks, idx):
    return [landmarks[idx].x, landmarks[idx].y]


# ===================== EXERCISES =====================
def analyze_biceps_curl(landmarks):
    shoulder = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_SHOULDER.value)
    elbow = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_ELBOW.value)
    wrist = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_WRIST.value)

    angle = calculate_angle(shoulder, elbow, wrist)
    stage = "DOWN" if angle > 160 else "UP" if angle < 35 else "MID"
    form = "GOOD" if 30 <= angle <= 170 else "BAD"
    return angle, stage, form


def analyze_deadlift(landmarks):
    shoulder = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_SHOULDER.value)
    hip = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_HIP.value)
    knee = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_KNEE.value)

    angle = calculate_angle(shoulder, hip, knee)
    stage = "up" if angle > 160 else "down" if angle < 120 else "mid"
    form = "GOOD" if 100 <= angle <= 180 else "BAD"
    return angle, stage, form


def analyze_triceps_pushdown(landmarks):
    shoulder = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_SHOULDER.value)
    elbow = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_ELBOW.value)
    wrist = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_WRIST.value)

    angle = calculate_angle(shoulder, elbow, wrist)
    stage = "down" if angle < 60 else "up" if angle > 140 else "mid"
    form = "GOOD" if 50 <= angle <= 160 else "BAD"
    return angle, stage, form


def analyze_leg_press(landmarks):
    hip = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_HIP.value)
    knee = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_KNEE.value)
    ankle = get_landmark(landmarks, mp_pose.PoseLandmark.LEFT_ANKLE.value)

    angle = calculate_angle(hip, knee, ankle)
    stage = "up" if angle > 160 else "down" if angle < 80 else "mid"
    form = "GOOD" if 70 <= angle <= 180 else "BAD"
    return angle, stage, form


EXERCISES = {
    "biceps_curl": analyze_biceps_curl,
    "deadlift": analyze_deadlift,
    "triceps_pushdown": analyze_triceps_pushdown,
    "leg_press": analyze_leg_press,
}


# ===================== VIDEO PROCESSING =====================
def process_video_and_draw(input_path, output_path, analyze_func):
    cap = cv2.VideoCapture(input_path)

    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fps = cap.get(cv2.CAP_PROP_FPS)

    writer = cv2.VideoWriter(
        output_path,
        cv2.VideoWriter_fourcc(*"mp4v"),
        fps,
        (width, height),
    )

    while cap.isOpened():
        ret, frame = cap.read()
        if not ret:
            break

        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = pose.process(rgb)

        if results.pose_landmarks:
            angle, stage, form = analyze_func(results.pose_landmarks.landmark)

            # Draw skeleton
            mp_drawing.draw_landmarks(
                frame, results.pose_landmarks, mp_pose.POSE_CONNECTIONS
            )

            # Draw text
            color = (0, 255, 0) if form == "GOOD" else (0, 0, 255)

            cv2.putText(
                frame,
                f"Angle: {angle}",
                (20, 40),
                cv2.FONT_HERSHEY_SIMPLEX,
                1,
                (255, 255, 255),
                2,
            )

            cv2.putText(
                frame,
                f"Stage: {stage}",
                (20, 90),
                cv2.FONT_HERSHEY_SIMPLEX,
                1,
                (255, 255, 255),
                2,
            )

            cv2.putText(
                frame, f"Form: {form}", (20, 140), cv2.FONT_HERSHEY_SIMPLEX, 1, color, 3
            )

        else:
            cv2.putText(
                frame,
                "No Pose Detected",
                (20, 40),
                cv2.FONT_HERSHEY_SIMPLEX,
                1,
                (0, 0, 255),
                2,
            )

        writer.write(frame)

    cap.release()
    writer.release()


# ===================== API =====================
@app.post("/analyze-video")
async def analyze_video(exercise: str = Form(...), file: UploadFile = File(...)):
    if exercise not in EXERCISES:
        raise HTTPException(400, "Invalid exercise")

    if not file.content_type.startswith("video/"):
        raise HTTPException(400, "Upload a video file")

    analyze_func = EXERCISES[exercise]

    # Save input video
    input_temp = tempfile.NamedTemporaryFile(delete=False, suffix=".mp4")
    input_temp.write(await file.read())
    input_temp.close()

    # Output video
    output_path = input_temp.name.replace(".mp4", "_processed.mp4")

    # Process
    process_video_and_draw(input_temp.name, output_path, analyze_func)

    # Cleanup input video
    os.remove(input_temp.name)

    # Return processed video
    return FileResponse(
        output_path, media_type="video/mp4", filename="corrected_video.mp4"
    )
