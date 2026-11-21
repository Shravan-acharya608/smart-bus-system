package com.smartbus.app.data.repository

import com.smartbus.app.data.local.UserPreferences
import com.smartbus.app.data.model.*
import com.smartbus.app.data.remote.ApiService
import kotlinx.coroutines.flow.Flow

class AuthRepository(
    private val apiService: ApiService,
    private val userPreferences: UserPreferences
) {
    val accessTokenFlow: Flow<String?> = userPreferences.accessTokenFlow
    val userRoleFlow: Flow<UserRole?> = userPreferences.userRoleFlow
    val userNameFlow: Flow<String?> = userPreferences.userNameFlow
    val baseUrlFlow: Flow<String> = userPreferences.baseUrlFlow

    suspend fun login(email: String, password: String, role: UserRole?): Result<User> {
        return try {
            val response = apiService.login(LoginRequest(email, password, role))
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()!!.data!!
                userPreferences.saveAuth(
                    accessToken = data.accessToken,
                    refreshToken = data.refreshToken,
                    userId = data.user.id,
                    email = data.user.email,
                    name = "${data.user.firstName} ${data.user.lastName}",
                    role = data.user.role
                )
                Result.success(data.user)
            } else {
                val msg = response.body()?.error?.message ?: response.message() ?: "Authentication failed"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(email: String, password: String, firstName: String, lastName: String, phone: String?): Result<User> {
        return try {
            val response = apiService.register(RegisterRequest(email, password, firstName, lastName, phone))
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()!!.data!!
                userPreferences.saveAuth(
                    accessToken = data.accessToken,
                    refreshToken = data.refreshToken,
                    userId = data.user.id,
                    email = data.user.email,
                    name = "${data.user.firstName} ${data.user.lastName}",
                    role = data.user.role
                )
                Result.success(data.user)
            } else {
                val msg = response.body()?.error?.message ?: response.message() ?: "Registration failed"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        try {
            apiService.logout(emptyMap())
        } catch (e: Exception) {
            // Ignore network errors on logout
        } finally {
            userPreferences.clearAuth()
        }
    }

    suspend fun setBaseUrl(url: String) {
        userPreferences.setBaseUrl(url)
    }
}
