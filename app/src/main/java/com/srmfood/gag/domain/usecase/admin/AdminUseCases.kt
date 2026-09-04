package com.srmfood.gag.domain.usecase.admin

import com.srmfood.gag.domain.repository.AdminRepository
import javax.inject.Inject

class GetSystemStatsUseCase @Inject constructor(
    private val adminRepository: AdminRepository
) {
    suspend operator fun invoke(): Result<SystemStats> = adminRepository.getSystemStats()
}

class ToggleOutletStatusUseCase @Inject constructor(
    private val adminRepository: AdminRepository
) {
    suspend operator fun invoke(outletId: String, isOpen: Boolean): Result<Unit> =
        adminRepository.toggleOutletStatus(outletId, isOpen)
}
