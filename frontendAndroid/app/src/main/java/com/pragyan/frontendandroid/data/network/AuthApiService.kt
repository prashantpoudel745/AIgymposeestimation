package com.pragyan.frontendandroid.data.network

import com.pragyan.frontendandroid.domain.model.AuthResponse
import com.pragyan.frontendandroid.domain.model.LoginRequest
import com.pragyan.frontendandroid.domain.model.RegisterRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
}
