package com.srmfood.gag.domain.usecase.auth

import com.srmfood.gag.domain.model.User
import com.srmfood.gag.domain.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke() = authRepository.logout()
}

class GetCurrentUserUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke() = authRepository.getCurrentUser()
}

class GetUserRoleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke() = authRepository.getUserRole()
}

class IsLoggedInUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke() = authRepository.isLoggedIn()
}

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        name: String,
        email: String,
        password: String,
        phone: String?,
        registrationNumber: String?
    ): Result<com.srmfood.gag.domain.repository.AuthResult> {
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Name cannot be empty"))
        if (email.isBlank()) return Result.failure(IllegalArgumentException("Email cannot be empty"))
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return Result.failure(IllegalArgumentException("Invalid email format"))
        }
        if (password.length < 8) {
            return Result.failure(IllegalArgumentException("Password must be at least 8 characters"))
        }
        return authRepository.register(name.trim(), email.trim(), password, phone, registrationNumber)
    }
}
