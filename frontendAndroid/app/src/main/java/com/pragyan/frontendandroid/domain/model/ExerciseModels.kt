package com.pragyan.frontendandroid.domain.model

import com.google.gson.annotations.SerializedName

data class AnalyzeVideoResponse(
    @SerializedName("exercise_type") val exerciseType: String,
    val side: String,
    val reps: Int,
    @SerializedName("detection_rate") val detectionRate: Float,
    @SerializedName("video_url") val videoUrl: String? = null
)

data class ExerciseRecordRequest(
    @SerializedName("exercise_type") val exerciseType: String,
    val side: String,
    val reps: Int,
    @SerializedName("detection_rate") val detectionRate: Float
)

data class ExerciseRecordResponse(
    val message: String,
    @SerializedName("id") val id: String? = null
)
