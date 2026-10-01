package com.srmfood.gag.data.repository

import com.srmfood.gag.domain.repository.AdminRepository
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ToggleOutletStatusParams(
    @SerialName("p_outlet_id") val outletId: String,
    @SerialName("p_is_open") val isOpen: Boolean
)

@Singleton
class AdminRepositoryImpl @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: Auth
) : AdminRepository {

    override suspend fun getManagementAnalytics(): Result<com.srmfood.gag.domain.usecase.admin.ManagementAnalytics> = runCatching {
        if (auth.currentSessionOrNull() == null) throw Exception("User not logged in")
        postgrest.rpc("get_management_analytics").decodeAs<com.srmfood.gag.domain.usecase.admin.ManagementAnalytics>()
    }

    override suspend fun getInventory(): Result<List<com.srmfood.gag.domain.usecase.admin.ManagementInventoryItem>> = runCatching {
        if (auth.currentSessionOrNull() == null) throw Exception("User not logged in")
        
        val dtos = postgrest.from("inventory")
            .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("id, food_item_id, quantity_available, low_stock_threshold, updated_at, food_items(id, name, image_url, price, is_available, outlets(name), categories(name))"))
            .decodeList<com.srmfood.gag.data.remote.dto.InventoryDto>()

        dtos.mapNotNull { dto ->
            val food = dto.foodItem ?: return@mapNotNull null
            com.srmfood.gag.domain.usecase.admin.ManagementInventoryItem(
                id = dto.id,
                foodItemId = dto.foodItemId,
                foodName = food.name,
                foodImage = food.imageUrl,
                outletName = food.outlet?.name ?: "Unknown",
                categoryName = food.category?.name ?: "Unknown",
                quantityAvailable = dto.quantityAvailable,
                lowStockThreshold = dto.lowStockThreshold,
                isFoodAvailable = food.isAvailable,
                price = food.price,
                updatedAt = dto.updatedAt
            )
        }
    }

    override suspend fun toggleOutletStatus(outletId: String, isOpen: Boolean): Result<Unit> = runCatching {
        if (auth.currentSessionOrNull() == null) throw Exception("User not logged in")
        postgrest.rpc(
            function = "admin_toggle_outlet_status",
            parameters = ToggleOutletStatusParams(outletId, isOpen)
        )
    }
}
