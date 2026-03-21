package com.pragyan.frontendandroid.domain.model

import com.google.gson.annotations.SerializedName

data class ExerciseRecord(
    @SerializedName("_id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("exercise_type") val exerciseType: String,
    val side: String,
    val reps: Int,
    @SerializedName("detection_rate") val detectionRate: Float,
    @SerializedName("processed_video_url") val processedVideoUrl: String?,
    @SerializedName("original_video_url") val originalVideoUrl: String?,
    val timestamp: String
)

data class HistoryResponse(
    val success: Boolean,
    val records: List<ExerciseRecord>
)
