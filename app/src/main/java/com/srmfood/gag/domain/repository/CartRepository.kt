package com.srmfood.gag.domain.repository

import com.srmfood.gag.domain.model.Cart
import com.srmfood.gag.domain.model.CartItem
import com.srmfood.gag.domain.model.SelectedCustomization
import kotlinx.coroutines.flow.Flow

interface CartRepository {
    fun getCart(): Flow<Cart?>
    suspend fun syncCart(): Result<Unit>
    suspend fun addToCart(
        foodItemId: String,
        foodName: String,
        foodImageUrl: String?,
        outletId: String,
        outletName: String,
        price: Double,
        quantity: Int,
        isVeg: Boolean,
        selectedCustomizations: List<SelectedCustomization>,
        specialInstructions: String?
    ): Result<Cart>
    suspend fun updateQuantity(cartItemId: String, quantity: Int): Result<Cart>
    suspend fun removeItem(cartItemId: String): Result<Cart>
    suspend fun clearCart(): Result<Unit>
    suspend fun getCartOutletId(): String?
}
