package com.srmfood.gag.domain.repository

import com.srmfood.gag.domain.usecase.admin.SystemStats

interface AdminRepository {
    suspend fun getSystemStats(): Result<SystemStats>
    suspend fun toggleOutletStatus(outletId: String, isOpen: Boolean): Result<Unit>
}
