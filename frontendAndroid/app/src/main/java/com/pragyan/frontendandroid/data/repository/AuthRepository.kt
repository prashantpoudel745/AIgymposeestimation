package com.pragyan.frontendandroid.data.repository

import com.pragyan.frontendandroid.data.network.AuthApiService
import com.pragyan.frontendandroid.domain.model.AuthResponse
import com.pragyan.frontendandroid.domain.model.LoginRequest
import com.pragyan.frontendandroid.domain.model.RegisterRequest
import retrofit2.Response

class AuthRepository(private val apiService: AuthApiService) {
    suspend fun register(request: RegisterRequest): Response<AuthResponse> {
        return apiService.register(request)
    }

    suspend fun login(request: LoginRequest): Response<AuthResponse> {
        return apiService.login(request)
    }
}
