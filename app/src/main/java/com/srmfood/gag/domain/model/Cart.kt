package com.srmfood.gag.domain.model

import kotlinx.serialization.Serializable

/**
 * Cart domain model.
 * A cart is always associated with a SINGLE outlet.
 * Attempting to add items from a different outlet requires cart clearing.
 */
@Serializable
data class Cart(
    val outletId: String,
    val outletName: String,
    val items: List<CartItem>,
    val subtotal: Double,
    val tax: Double,
    val total: Double,
    val estimatedPrepMinutes: Int
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val totalItems: Int get() = items.sumOf { it.quantity }
}

@Serializable
data class CartItem(
    val id: String,
    val foodItemId: String,
    val foodName: String,
    val foodImageUrl: String?,
    val outletId: String,
    val price: Double,
    val quantity: Int,
    val selectedCustomizations: List<SelectedCustomization>,
    val isVeg: Boolean,
    val specialInstructions: String?
) {
    val itemTotal: Double get() = (price + selectedCustomizations.sumOf { it.extraPrice }) * quantity
}

@Serializable
data class SelectedCustomization(
    val customizationId: String,
    val customizationName: String,
    val optionId: String,
    val optionName: String,
    val extraPrice: Double
)
