package com.pragyan.frontendandroid.data.network

import com.pragyan.frontendandroid.domain.model.HistoryResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ExerciseApiService {
    @Multipart
    @POST("analyze-video")
    suspend fun analyzeVideo(
        @Header("Authorization") token: String,
        @Part("exercise") exercise: RequestBody,
        @Part("side") side: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<ResponseBody>

    @GET("fetch-history")
    suspend fun fetchHistory(
        @Header("Authorization") token: String,
        @Query("exercise_type") exerciseType: String? = null,
        @Query("side") side: String? = null
    ): Response<HistoryResponse>
}
