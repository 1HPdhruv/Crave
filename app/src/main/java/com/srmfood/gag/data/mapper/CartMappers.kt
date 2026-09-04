package com.srmfood.gag.data.mapper

import com.srmfood.gag.data.local.entity.CartItemEntity
import com.srmfood.gag.domain.model.CartItem
import com.srmfood.gag.domain.model.SelectedCustomization
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun CartItemEntity.toDomain(): CartItem = CartItem(
    id = id,
    foodItemId = foodItemId,
    foodName = foodName,
    foodImageUrl = foodImageUrl,
    outletId = outletId,
    price = price,
    quantity = quantity,
    selectedCustomizations = try { Json.decodeFromString(selectedCustomizations) } catch (e: Exception) { emptyList() },
    isVeg = isVeg,
    specialInstructions = specialInstructions
)

fun CartItem.toEntity(outletName: String): CartItemEntity = CartItemEntity(
    id = id,
    foodItemId = foodItemId,
    foodName = foodName,
    foodImageUrl = foodImageUrl,
    outletId = outletId,
    outletName = outletName,
    price = price,
    quantity = quantity,
    selectedCustomizations = Json.encodeToString(selectedCustomizations),
    isVeg = isVeg,
    specialInstructions = specialInstructions
)
