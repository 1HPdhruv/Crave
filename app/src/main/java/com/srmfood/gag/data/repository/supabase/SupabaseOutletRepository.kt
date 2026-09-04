package com.srmfood.gag.data.repository.supabase

import com.srmfood.gag.data.local.dao.OutletDao
import com.srmfood.gag.data.mapper.toDomain
import com.srmfood.gag.data.mapper.toEntity
import com.srmfood.gag.domain.model.OperatingHours
import com.srmfood.gag.domain.model.Outlet
import com.srmfood.gag.domain.model.OutletLocation
import com.srmfood.gag.domain.repository.OutletRepository
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class OutletDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val image_url: String? = null,
    val vendor_id: String? = null,
    val is_open: Boolean = false,
    val is_active: Boolean = true,
    val phone: String? = null,
    val building: String? = null,
    val floor: String? = null,
    val location_description: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val operating_hours: OperatingHoursDto? = null,
    val rating: Double = 0.0,
    val total_reviews: Int = 0
)

@Serializable
data class OperatingHoursDto(
    val openTime: String = "08:00",
    val closeTime: String = "22:00",
    val daysOpen: List<String> = emptyList()
)

@Singleton
class SupabaseOutletRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val outletDao: OutletDao
) : OutletRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getOutlets(): Flow<List<Outlet>> {
        return outletDao.observeOutlets().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun refreshOutlets(): Result<List<Outlet>> = runCatching {
        android.util.Log.d("SupabaseOutletRepo", "refreshOutlets: Querying real Supabase database for active outlets...")
        val dtos = postgrest["outlets"].select {
            filter { eq("is_active", true) }
        }.decodeList<OutletDto>()
        android.util.Log.d("SupabaseOutletRepo", "refreshOutlets: Successfully retrieved ${dtos.size} outlets.")
        
        val outlets: List<Outlet> = dtos.map { it.toDomain() }
        val entities = outlets.map { it.toEntity() }
        outletDao.insertAll(entities)
        
        outlets
    }.onFailure {
        android.util.Log.e("SupabaseOutletRepo", "refreshOutlets: Failed to retrieve outlets from Supabase", it)
    }

    override suspend fun getOutletById(outletId: String): Result<Outlet> = runCatching {
        val local = outletDao.getOutletById(outletId)
        if (local != null) return@runCatching local.toDomain()

        val dto = postgrest["outlets"].select {
            filter { eq("id", outletId) }
        }.decodeSingle<OutletDto>()
        
        val outlet: Outlet = dto.toDomain()
        outletDao.insertAll(listOf(outlet.toEntity()))
        outlet
    }

    override suspend fun getNearbyOutlets(lat: Double, lng: Double): Result<List<Outlet>> = runCatching {
        // In a real scenario, use PostGIS or a custom RPC for distance.
        // For now, fetch all and rely on the UI/Domain layer.
        val dtos = postgrest["outlets"].select {
            filter { eq("is_active", true) }
        }.decodeList<OutletDto>()
        dtos.map { it.toDomain() }
    }

    private fun OutletDto.toDomain(): Outlet {
        return Outlet(
            id = id,
            name = name,
            description = description ?: "",
            imageUrl = image_url,
            location = OutletLocation(
                building = building ?: "",
                floor = floor ?: "",
                description = location_description ?: "",
                latitude = latitude,
                longitude = longitude
            ),
            isOpen = is_open,
            operatingHours = OperatingHours(
                openTime = operating_hours?.openTime ?: "08:00",
                closeTime = operating_hours?.closeTime ?: "22:00",
                daysOpen = operating_hours?.daysOpen ?: emptyList()
            ),
            currentQueueSize = 0, // In real scenario, fetch from a real-time table or queue table
            estimatedWaitMinutes = 0,
            categories = emptyList(), // Can be fetched from a junction table if needed
            rating = rating,
            totalReviews = total_reviews,
            vendorId = vendor_id ?: "",
            isActive = is_active,
            phone = phone
        )
    }

    private fun Outlet.toEntity(): com.srmfood.gag.data.local.entity.OutletEntity {
        return com.srmfood.gag.data.local.entity.OutletEntity(
            id = id,
            name = name,
            description = description,
            imageUrl = imageUrl,
            building = location.building,
            floor = location.floor,
            locationDescription = location.description,
            latitude = location.latitude,
            longitude = location.longitude,
            isOpen = isOpen,
            openTime = operatingHours.openTime,
            closeTime = operatingHours.closeTime,
            daysOpen = Json.encodeToString(operatingHours.daysOpen),
            rating = rating,
            totalReviews = totalReviews,
            vendorId = vendorId,
            isActive = isActive,
            phone = phone,
            categories = Json.encodeToString(categories),
            currentQueueSize = currentQueueSize,
            estimatedWaitMinutes = estimatedWaitMinutes
        )
    }
}
