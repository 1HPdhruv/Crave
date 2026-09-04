package com.srmfood.gag.domain.model

/**
 * Core user domain model.
 * Never expose this directly from DTOs — always map through mapper.
 */
data class User(
    val id: String,
    val name: String,
    val email: String,
    val phone: String?,
    val role: UserRole,
    val profileImageUrl: String?,
    val registrationNumber: String?,   // SRM student reg number
    val isActive: Boolean,
    val createdAt: String
)

enum class UserRole {
    STUDENT, VENDOR, ADMIN;

    companion object {
        fun fromString(role: String): UserRole = when (role.uppercase()) {
            "STUDENT" -> STUDENT
            "VENDOR" -> VENDOR
            "ADMIN" -> ADMIN
            else -> STUDENT
        }
    }
}

data class AuthState(
    val isLoggedIn: Boolean,
    val user: User?,
    val role: UserRole?
)
