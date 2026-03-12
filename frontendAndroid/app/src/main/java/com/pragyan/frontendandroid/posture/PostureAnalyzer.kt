package com.pragyan.frontendandroid.posture

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.pragyan.frontendandroid.domain.model.Exercise
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

        val (shoulder, elbow, wrist) =
            getLandmarks(landmarks, "shoulder", "elbow", "wrist")

        val angle = AngleUtils.calculateAngle(shoulder, elbow, wrist)

        if (angle > 130 && stage != "down") {
            stage = "down"
        }

        if (angle < 60 && stage == "down") {
            stage = "up"
            counter++
        }

        val formStatus =
            if (angle in 30..170) "GOOD" else "BAD"

        formIssues.clear()

        when {
            angle < 30 -> formIssues.add("Elbow overextended")
            angle > 170 -> formIssues.add("Not full contraction")
        }

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