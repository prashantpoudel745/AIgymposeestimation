package com.pragyan.frontendandroid.posture

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.pragyan.frontendandroid.domain.model.ExerciseType

class PostureAnalyzer(
    private val exerciseType: ExerciseType,
    side: String = "left"
) {

    private var side: String = side.lowercase()

    var counter = 0
    var stage: String? = null
    var formIssues: MutableList<String> = mutableListOf()

    private val sideMap = mapOf(
        "left" to mapOf(
            "shoulder" to 11,
            "elbow" to 13,
            "wrist" to 15,
            "hip" to 23,
            "knee" to 25,
            "ankle" to 27
        ),
        "right" to mapOf(
            "shoulder" to 12,
            "elbow" to 14,
            "wrist" to 16,
            "hip" to 24,
            "knee" to 26,
            "ankle" to 28
        )
    )

    private fun getLandmarks(
        landmarks: List<NormalizedLandmark>,
        vararg keys: String
    ): List<List<Float>> {

        val lmSet = sideMap[side] ?: throw IllegalArgumentException("Invalid side")

        val result = mutableListOf<List<Float>>()

        for (key in keys) {

            val index = lmSet[key]
                ?: throw IllegalArgumentException("Invalid landmark key")

            val lm = LandmarkUtils.getLandmark(landmarks, index)
                ?: throw IllegalArgumentException("Landmark $key not visible")

            result.add(lm)
        }

        return result
    }

    fun analyze(landmarks: List<NormalizedLandmark>): Triple<Int?, String, String> {

        return try {

            when (exerciseType) {

                ExerciseType.BICEPS_CURL ->
                    analyzeBicepsCurl(landmarks)

                ExerciseType.DEADLIFT ->
                    analyzeDeadlift(landmarks)

                ExerciseType.TRICEPS_PUSHDOWN ->
                    analyzeTricepsPushdown(landmarks)

                ExerciseType.LEG_PRESS ->
                    analyzeLegPress(landmarks)
            }

        } catch (e: Exception) {

            Triple(null, "undetected", "LANDMARK_ERROR: ${e.message}")
        }
    }

    private fun analyzeBicepsCurl(
        landmarks: List<NormalizedLandmark>
    ): Triple<Int, String, String> {


        // ── Core landmarks ────────────────────────────────────────────────────────
        val (shoulder, elbow, wrist) =
            getLandmarks(landmarks, "shoulder", "elbow", "wrist")

        // Hip is used to detect trunk / body sway
        val hip = try {
            getLandmarks(landmarks, "hip").first()
        } catch (e: Exception) {
            null
        }

        // ── Primary angle: shoulder → elbow → wrist (elbow flexion) ──────────────
        val angle = AngleUtils.calculateAngle(shoulder, elbow, wrist)

        // ── Stage machine ─────────────────────────────────────────────────────────
        // "down"  = arm extended, ready to curl   (angle > 150°)
        // "up"    = arm curled, peak contraction  (angle < 50°)
        // Hysteresis band between 50–150 prevents rapid toggling ("mid")
        if (angle > 130 && stage != "down") {
            stage = "down"
        }
//             Transition out of "up" as soon as the arm starts descending
//            if (angle > 80 && stage == "up") {
//                stage = "mid"
//            }

        if (angle < 50 && stage == "down") {
            stage = "up"
            counter++
        }

        // ── Form checks ───────────────────────────────────────────────────────────
        formIssues.clear()

        // 1. Hyperextension at the bottom — angle > 170° means elbow is locking out
        if (stage == "down" && angle > 160) {
            formIssues.add("Don't lock out your elbow at the bottom")
        }

        // 2. Incomplete contraction at the top — angle > 60° at "up" means
        //    the user didn't fully curl the weight
//            if (stage == "up" && angle > 70) {
//                formIssues.add("Curl higher — incomplete contraction at peak")
//            }

        // 3. Elbow sway / upper arm drift — the upper arm (shoulder→elbow) should
        //    stay close to vertical (elbow below shoulder).
        //    We compare the elbow's x position to the shoulder's x position.
        //    In normalised coords x grows left→right; a large horizontal gap means
        //    the elbow has swung forward.

        val dx = elbow[0] - shoulder[0]   // horizontal difference
        val dy = elbow[1] - shoulder[1]   // vertical difference

        val angleRad = kotlin.math.atan2(kotlin.math.abs(dx), kotlin.math.abs(dy))
        val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()

        if (angleDeg > 25f) {
            formIssues.add("Keep your elbow tucked — upper arm is swinging")
        }


        // 4. Wrist curl / wrist drop — wrist should not drop below elbow at the top
        //    (y grows downward in image coords)
//            if (stage == "up" && wrist[1] > elbow[1]) {
//                formIssues.add("Keep your wrist above elbow level at the top")
//            }

        // 5. Trunk / body sway — if hip is visible, check that the torso is upright.
        //    Shoulder and hip should be roughly vertically aligned.
        if (hip != null) {

            val dx = shoulder[0] - hip[0]   // horizontal difference
            val dy = shoulder[1] - hip[1]   // vertical difference

            val angleRad = kotlin.math.atan2(kotlin.math.abs(dx), kotlin.math.abs(dy))
            val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()

            if (angleDeg > 15f) {
                formIssues.add("Stand upright — avoid swinging your torso")
            }
        }
        // ── Overall form status ───────────────────────────────────────────────────
        val formStatus = if (formIssues.isEmpty()) "GOOD" else "BAD"

        return Triple(angle, stage ?: "mid", formStatus)
    }

    private fun analyzeDeadlift(
        landmarks: List<NormalizedLandmark>
    ): Triple<Int, String, String> {

        val (shoulder, hip, knee) =
            getLandmarks(landmarks, "shoulder", "hip", "knee")

        val angle = AngleUtils.calculateAngle(shoulder, hip, knee)

        val formStatus =
            if (angle in 100..180) "GOOD" else "BAD"

        formIssues.clear()

        when {
            angle < 100 -> formIssues.add("Back rounding - risk of injury!")
            angle > 170 -> formIssues.add("Insufficient hip hinge")
        }

        return Triple(angle, "mid", formStatus)
    }

    private fun analyzeTricepsPushdown(
        landmarks: List<NormalizedLandmark>
    ): Triple<Int, String, String> {

        val (shoulder, elbow, wrist) =
            getLandmarks(landmarks, "shoulder", "elbow", "wrist")

        val angle = AngleUtils.calculateAngle(shoulder, elbow, wrist)

        val formStatus =
            if (angle in 50..160) "GOOD" else "BAD"

        formIssues.clear()

        when {
            angle < 50 -> formIssues.add("Elbow flaring")
            angle > 160 -> formIssues.add("Incomplete extension")
        }

        return Triple(angle, "mid", formStatus)
    }

    private fun analyzeLegPress(
        landmarks: List<NormalizedLandmark>
    ): Triple<Int, String, String> {

        val (hip, knee, ankle) =
            getLandmarks(landmarks, "hip", "knee", "ankle")

        val angle = AngleUtils.calculateAngle(hip, knee, ankle)

        val formStatus =
            if (angle in 70..180) "GOOD" else "BAD"

        formIssues.clear()

        when {
            angle < 70 -> formIssues.add("Knee overextension - injury risk!")
            angle > 170 -> formIssues.add("Incomplete range of motion")
        }

        return Triple(angle, "mid", formStatus)
    }
}