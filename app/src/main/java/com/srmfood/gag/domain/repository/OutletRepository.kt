package com.srmfood.gag.domain.repository

import com.srmfood.gag.domain.model.Outlet
import kotlinx.coroutines.flow.Flow

interface OutletRepository {
    fun getOutlets(): Flow<List<Outlet>>
    suspend fun refreshOutlets(): Result<List<Outlet>>
    suspend fun getOutletById(outletId: String): Result<Outlet>
    suspend fun getNearbyOutlets(lat: Double, lng: Double): Result<List<Outlet>>
}
