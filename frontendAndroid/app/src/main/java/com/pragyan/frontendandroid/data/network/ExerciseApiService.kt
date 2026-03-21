package com.pragyan.frontendandroid.data.network

import com.pragyan.frontendandroid.domain.model.ExerciseRecordRequest
import com.pragyan.frontendandroid.domain.model.ExerciseRecordResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ExerciseApiService {
    @Multipart
    @POST("analyze-video")
    suspend fun analyzeVideo(
        @Header("Authorization") token: String,
        @Part("exercise") exercise: RequestBody,
        @Part("side") side: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<ResponseBody>

    @POST("exercise-record")
    suspend fun saveExerciseRecord(
        @Header("Authorization") token: String,
        @Body request: ExerciseRecordRequest
    ): Response<ExerciseRecordResponse>
}
