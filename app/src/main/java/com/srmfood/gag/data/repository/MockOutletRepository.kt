package com.srmfood.gag.data.repository

import com.srmfood.gag.data.local.dao.OutletDao
import com.srmfood.gag.data.mapper.toDomain
import com.srmfood.gag.data.mapper.toEntity
import com.srmfood.gag.data.remote.api.OutletApi
import com.srmfood.gag.domain.model.Outlet
import com.srmfood.gag.domain.repository.OutletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockOutletRepository @Inject constructor(
    private val api: OutletApi,
    private val outletDao: OutletDao
) : OutletRepository {

    override fun getOutlets(): Flow<List<Outlet>> {
        return outletDao.observeOutlets().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun refreshOutlets(): Result<List<Outlet>> = runCatching {
        val response = api.getOutlets()
        val entities = response.outlets.map { it.toEntity() }
        outletDao.insertAll(entities)
        entities.map { it.toDomain() }
    }

    override suspend fun getOutletById(outletId: String): Result<Outlet> = runCatching {
        // Try local first
        val local = outletDao.getOutletById(outletId)
        if (local != null) return@runCatching local.toDomain()

        // Fallback to remote
        val remote = api.getOutletById(outletId)
        outletDao.insertAll(listOf(remote.toEntity()))
        remote.toDomain()
    }

    override suspend fun getNearbyOutlets(lat: Double, lng: Double): Result<List<Outlet>> = runCatching {
        // Mocking nearby logic for now, usually the API would handle this
        val response = api.getOutlets()
        response.outlets.map { it.toDomain() }
    }
}
