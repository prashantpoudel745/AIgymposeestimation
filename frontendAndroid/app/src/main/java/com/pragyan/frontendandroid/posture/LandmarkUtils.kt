package com.pragyan.frontendandroid.posture

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

object LandmarkUtils {

    fun getLandmark(
        landmarks: List<NormalizedLandmark>,
        index: Int
    ): List<Float>? {

        if (index >= landmarks.size) {
            throw IllegalArgumentException("Landmark index $index out of range")
        }

        val lm = landmarks[index]

        return if (lm.visibility().orElse(0f) > 0.5f) {
            listOf(lm.x(), lm.y())
        } else {
            null
        }
    }
}