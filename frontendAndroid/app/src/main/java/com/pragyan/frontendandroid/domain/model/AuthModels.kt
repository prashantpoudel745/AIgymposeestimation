package com.pragyan.frontendandroid.domain.model

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("full_name") val fullName: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    val user: User? = null
)

data class User(
    @SerializedName("_id") val id: String?,
    @SerializedName("full_name") val fullName: String?,
    val email: String
)
