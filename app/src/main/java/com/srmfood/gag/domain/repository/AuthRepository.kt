package com.srmfood.gag.domain.repository

import com.srmfood.gag.domain.model.User
import com.srmfood.gag.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    object EmailConfirmationRequired : AuthResult()
}

interface AuthRepository {
    /** Login with email and password. Returns the authenticated User on success. */
    suspend fun login(email: String, password: String): Result<User>

    /** Register a new student account. */
    suspend fun register(
        name: String,
        email: String,
        password: String,
        phone: String?,
        registrationNumber: String?
    ): Result<AuthResult>

    /** Logout — clears tokens and local user data. */
    suspend fun logout()

    /** Returns the currently authenticated user, or null if not logged in. */
    fun getCurrentUser(): Flow<User?>

    /** Returns true if a valid session exists. */
    suspend fun isLoggedIn(): Boolean

    /** Returns the user's role. */
    suspend fun getUserRole(): UserRole?

    /** Update FCM token on server. */
    suspend fun updateFcmToken(token: String): Result<Unit>

    /** Update user profile fields. */
    suspend fun updateProfile(name: String, phone: String?, registrationNumber: String?): Result<User>
}
