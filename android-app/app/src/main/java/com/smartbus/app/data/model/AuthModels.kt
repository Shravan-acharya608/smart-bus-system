package com.smartbus.app.data.model

import com.google.gson.annotations.SerializedName

enum class UserRole {
    PASSENGER,
    DRIVER,
    CONDUCTOR,
    BUS_OWNER,
    ADMIN
}

data class User(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: UserRole,
    val phone: String? = null,
    val organizationId: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String,
    val role: UserRole? = null
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val phone: String? = null
)

data class AuthResponseData(
    val user: User,
    val accessToken: String,
    val refreshToken: String
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
    val message: String? = null
)

data class ApiError(
    val code: String,
    val message: String
)
