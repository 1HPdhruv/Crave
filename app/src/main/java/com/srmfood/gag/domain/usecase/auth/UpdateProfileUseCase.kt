package com.srmfood.gag.domain.usecase.auth

import com.srmfood.gag.domain.model.User
import com.srmfood.gag.domain.repository.AuthRepository
import javax.inject.Inject

class UpdateProfileUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(name: String, phone: String?, registrationNumber: String?): Result<User> {
        return authRepository.updateProfile(name, phone, registrationNumber)
    }
}
