package com.srmfood.gag.data.repository

import com.srmfood.gag.data.local.dao.CartDao
import com.srmfood.gag.data.local.entity.CartItemEntity
import com.srmfood.gag.data.mapper.toDomain
import com.srmfood.gag.data.mapper.toEntity
import com.srmfood.gag.domain.model.Cart
import com.srmfood.gag.domain.model.CartItem
import com.srmfood.gag.domain.model.SelectedCustomization
import com.srmfood.gag.domain.repository.CartRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockCartRepository @Inject constructor(
    private val cartDao: CartDao
) : CartRepository {

    override fun getCart(): Flow<Cart?> {
        return cartDao.observeCartItems().map { entities ->
            if (entities.isEmpty()) return@map null

            val items = entities.map { it.toDomain() }
            val outletId = entities.first().outletId
            val outletName = entities.first().outletName

            val subtotal = items.sumOf { it.itemTotal }
            val tax = subtotal * 0.05 // 5% tax mock
            val total = subtotal + tax

            Cart(
                outletId = outletId,
                outletName = outletName,
                items = items,
                subtotal = subtotal,
                tax = tax,
                total = total,
                estimatedPrepMinutes = items.size * 5 // Mock prep time logic
            )
        }
    }
    override suspend fun syncCart(): Result<Unit> = Result.success(Unit)


    override suspend fun addToCart(
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
    ): Result<Cart> = runCatching {
        val currentOutletId = cartDao.getCartOutletId()
        if (currentOutletId != null && currentOutletId != outletId) {
            throw IllegalStateException("Cannot add items from different outlets")
        }

        val cartItem = CartItem(
            id = UUID.randomUUID().toString(),
            foodItemId = foodItemId,
            foodName = foodName,
            foodImageUrl = foodImageUrl,
            outletId = outletId,
            price = price,
            quantity = quantity,
            selectedCustomizations = selectedCustomizations,
            isVeg = isVeg,
            specialInstructions = specialInstructions
        )
        
        cartDao.insertItem(cartItem.toEntity(outletName))
        getCartSnapshot() ?: throw Exception("Cart empty after add")
    }

    override suspend fun updateQuantity(cartItemId: String, quantity: Int): Result<Cart> = runCatching {
        if (quantity <= 0) {
            cartDao.deleteItem(cartItemId)
        } else {
            val items = cartDao.getCartItems()
            val item = items.find { it.id == cartItemId }
            if (item != null) {
                cartDao.updateItem(item.copy(quantity = quantity))
            }
        }
        getCartSnapshot() ?: throw Exception("Cart empty")
    }

    override suspend fun removeItem(cartItemId: String): Result<Cart> = runCatching {
        cartDao.deleteItem(cartItemId)
        getCartSnapshot() ?: throw Exception("Cart empty")
    }

    override suspend fun clearCart(): Result<Unit> = runCatching {
        cartDao.clearCart()
    }

    override suspend fun getCartOutletId(): String? {
        return cartDao.getCartOutletId()
    }
    
    private suspend fun getCartSnapshot(): Cart? {
        val entities = cartDao.getCartItems()
        if (entities.isEmpty()) return null
        
        val items = entities.map { it.toDomain() }
        val subtotal = items.sumOf { it.itemTotal }
        val tax = subtotal * 0.05
        return Cart(
            outletId = entities.first().outletId,
            outletName = entities.first().outletName,
            items = items,
            subtotal = subtotal,
            tax = tax,
            total = subtotal + tax,
            estimatedPrepMinutes = items.size * 5
        )
    }
}
