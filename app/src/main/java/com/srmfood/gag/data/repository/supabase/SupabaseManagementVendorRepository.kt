package com.srmfood.gag.data.repository.supabase

import com.srmfood.gag.data.remote.dto.OutletDto
import com.srmfood.gag.domain.model.OperatingHours
import com.srmfood.gag.domain.model.Outlet
import com.srmfood.gag.domain.model.OutletLocation
import com.srmfood.gag.domain.model.UserRole
import com.srmfood.gag.domain.model.admin.VendorProfile
import com.srmfood.gag.domain.repository.ManagementVendorRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class VendorProfileDto(
    val id: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val role: String,
    val is_active: Boolean = true,
    val created_at: String,
    val outlets: List<OutletDto> = emptyList()
)

@Singleton
class SupabaseManagementVendorRepository @Inject constructor(
    private val postgrest: Postgrest
) : ManagementVendorRepository {

    override suspend fun getVendors(roleFilter: String?, searchQuery: String?): Result<List<VendorProfile>> = runCatching {
        val dtos = postgrest["profiles"].select(Columns.raw("*, outlets(*)")) {
            filter {
                if (roleFilter != null && roleFilter != "ALL") {
                    if (roleFilter.uppercase() == "SUSPENDED") {
                        eq("is_active", false)
                        isIn("role", listOf("VENDOR", "PENDING_VENDOR"))
                    } else {
                        eq("role", roleFilter.uppercase())
                        eq("is_active", true)
                    }
                } else {
                    isIn("role", listOf("VENDOR", "PENDING_VENDOR"))
                }
                
                if (!searchQuery.isNullOrBlank()) {
                    val q = searchQuery.trim()
                    or {
                        ilike("name", "%$q%")
                        ilike("email", "%$q%")
                    }
                }
            }
            order("created_at", Order.DESCENDING)
        }.decodeList<VendorProfileDto>()

        dtos.map { it.toDomain() }
    }

    override suspend fun getVendorById(vendorId: String): Result<VendorProfile> = runCatching {
        val dto = postgrest["profiles"].select(Columns.raw("*, outlets(*)")) {
            filter { eq("id", vendorId) }
        }.decodeSingle<VendorProfileDto>()
        
        dto.toDomain()
    }

    private fun VendorProfileDto.toDomain(): VendorProfile {
        return VendorProfile(
            id = id,
            name = name,
            email = email,
            phone = phone,
            role = UserRole.fromString(role),
            isActive = is_active,
            createdAt = created_at,
            outlets = outlets.map { dto ->
                Outlet(
                    id = dto.id,
                    name = dto.name,
                    description = dto.description ?: "",
                    imageUrl = dto.imageUrl,
                    location = OutletLocation(
                        building = dto.building ?: "",
                        floor = dto.floor ?: "",
                        description = dto.locationDescription ?: "",
                        latitude = dto.latitude,
                        longitude = dto.longitude
                    ),
                    isOpen = dto.isOpen,
                    operatingHours = OperatingHours(
                        openTime = dto.openTime,
                        closeTime = dto.closeTime,
                        daysOpen = dto.daysOpen
                    ),
                    currentQueueSize = dto.currentQueueSize,
                    estimatedWaitMinutes = dto.estimatedWaitMinutes,
                    categories = dto.categories,
                    rating = dto.rating,
                    totalReviews = dto.totalReviews,
                    vendorId = dto.vendorId ?: id,
                    isActive = dto.isActive,
                    phone = dto.phone
                )
            }
        )
    }
}
