package com.srmfood.gag.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InventoryDto(
    @SerialName("id") val id: String,
    @SerialName("food_item_id") val foodItemId: String,
    @SerialName("quantity_available") val quantityAvailable: Int,
    @SerialName("low_stock_threshold") val lowStockThreshold: Int,
    @SerialName("updated_at") val updatedAt: String,
    // Expanded relationships via PostgREST
    @SerialName("food_items") val foodItem: InventoryFoodItemDto? = null
)

@Serializable
data class InventoryFoodItemDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("price") val price: Double,
    @SerialName("is_available") val isAvailable: Boolean,
    @SerialName("outlets") val outlet: OutletNameDto? = null,
    @SerialName("categories") val category: CategoryNameDto? = null
)
