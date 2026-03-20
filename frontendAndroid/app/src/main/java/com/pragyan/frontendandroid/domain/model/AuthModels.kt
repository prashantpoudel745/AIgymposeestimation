package com.pragyan.frontendandroid.domain.model

data class RegisterRequest(
    val fullName: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val message: String,
    val token: String? = null,
    val user: User? = null
)

data class User(
    val id: Int,
    val fullName: String,
    val email: String
)
