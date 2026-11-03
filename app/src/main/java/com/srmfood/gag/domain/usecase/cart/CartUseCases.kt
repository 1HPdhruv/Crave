package com.srmfood.gag.domain.usecase.cart

import com.srmfood.gag.domain.model.Cart
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.model.SelectedCustomization
import com.srmfood.gag.domain.repository.CartRepository
import kotlinx.coroutines.flow.Flow
import com.srmfood.gag.domain.usecase.outlet.studentMessage
import javax.inject.Inject

class GetCartUseCase @Inject constructor(
    private val cartRepository: CartRepository
) {
    operator fun invoke(): Flow<Cart?> = cartRepository.getCart()
}

class SyncCartUseCase @Inject constructor(
    private val cartRepository: CartRepository
) {
    suspend operator fun invoke(): Result<String> = cartRepository.syncCart()
}

class AddToCartUseCase @Inject constructor(
    private val cartRepository: CartRepository,
    private val checkOutletAvailability: com.srmfood.gag.domain.usecase.outlet.CheckOutletAvailabilityUseCase
) {
    suspend operator fun invoke(
        foodItem: FoodItem,
        quantity: Int = 1,
        selectedCustomizations: List<SelectedCustomization> = emptyList(),
        specialInstructions: String? = null
    ): Result<Cart> {
        if (quantity < 1) return Result.failure(IllegalArgumentException("Quantity must be at least 1"))
        if (!foodItem.isAvailable) return Result.failure(IllegalStateException("Food item is not available"))
        // Authoritative server check; unreachable server => allow (order placement enforces it again).
        val availability = checkOutletAvailability(foodItem.outletId)
        if (availability is com.srmfood.gag.domain.usecase.outlet.OutletAvailability.Unavailable) {
            return Result.failure(
                com.srmfood.gag.domain.usecase.outlet.OutletUnavailableException(
                    availability.status, availability.status.studentMessage(availability.outletName)
                )
            )
        }
        return cartRepository.addToCart(
            foodItemId = foodItem.id,
            foodName = foodItem.name,
            foodImageUrl = foodItem.imageUrl,
            outletId = foodItem.outletId,
            outletName = foodItem.outletName,
            price = foodItem.price,
            quantity = quantity,
            isVeg = foodItem.isVeg,
            selectedCustomizations = selectedCustomizations,
            specialInstructions = specialInstructions
        )
    }
}

class UpdateCartItemUseCase @Inject constructor(
    private val cartRepository: CartRepository
) {
    suspend operator fun invoke(cartItemId: String, quantity: Int): Result<Cart> {
        if (quantity < 0) return Result.failure(IllegalArgumentException("Invalid quantity"))
        return if (quantity == 0) {
            cartRepository.removeItem(cartItemId)
        } else {
            cartRepository.updateQuantity(cartItemId, quantity)
        }
    }
}

class RemoveCartItemUseCase @Inject constructor(
    private val cartRepository: CartRepository
) {
    suspend operator fun invoke(cartItemId: String): Result<Cart> =
        cartRepository.removeItem(cartItemId)
}

class ClearCartUseCase @Inject constructor(
    private val cartRepository: CartRepository
) {
    suspend operator fun invoke(): Result<Unit> = cartRepository.clearCart()
}

class GetCartOutletIdUseCase @Inject constructor(
    private val cartRepository: CartRepository
) {
    suspend operator fun invoke(): String? = cartRepository.getCartOutletId()
}
