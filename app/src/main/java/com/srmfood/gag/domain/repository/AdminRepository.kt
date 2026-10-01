package com.srmfood.gag.domain.repository

import com.srmfood.gag.domain.usecase.admin.SystemStats
import com.srmfood.gag.domain.usecase.admin.ManagementAnalytics

interface AdminRepository {
    suspend fun getManagementAnalytics(): Result<ManagementAnalytics>
    suspend fun getInventory(): Result<List<com.srmfood.gag.domain.usecase.admin.ManagementInventoryItem>>
    suspend fun toggleOutletStatus(outletId: String, isOpen: Boolean): Result<Unit>
}
