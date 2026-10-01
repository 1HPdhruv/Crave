package com.srmfood.gag.domain.usecase.admin

import kotlinx.serialization.Serializable

@Serializable
data class ManagementInventoryItem(
    val id: String,
    val foodItemId: String,
    val foodName: String,
    val foodImage: String?,
    val outletName: String,
    val categoryName: String,
    val quantityAvailable: Int,
    val lowStockThreshold: Int,
    val isFoodAvailable: Boolean, // matches food_items.is_available
    val price: Double,
    val updatedAt: String
) {
    val stockStatus: StockStatus
        get() = when {
            quantityAvailable <= 0 -> StockStatus.OUT_OF_STOCK
            quantityAvailable <= lowStockThreshold -> StockStatus.LOW_STOCK
            else -> StockStatus.IN_STOCK
        }

    enum class StockStatus {
        IN_STOCK,
        LOW_STOCK,
        OUT_OF_STOCK
    }
}
