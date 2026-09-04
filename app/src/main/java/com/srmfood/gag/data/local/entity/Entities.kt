package com.srmfood.gag.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val phone: String?,
    val role: String,
    val profileImageUrl: String?,
    val registrationNumber: String?,
    val isActive: Boolean,
    val createdAt: String
)

@Entity(tableName = "outlets")
data class OutletEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val imageUrl: String?,
    val building: String,
    val floor: String,
    val locationDescription: String,
    val latitude: Double?,
    val longitude: Double?,
    val isOpen: Boolean,
    val openTime: String,
    val closeTime: String,
    val daysOpen: String,       // JSON array stored as string
    val currentQueueSize: Int,
    val estimatedWaitMinutes: Int,
    val categories: String,     // JSON array stored as string
    val rating: Double,
    val totalReviews: Int,
    val vendorId: String,
    val isActive: Boolean,
    val phone: String?,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "food_items")
data class FoodItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val imageUrl: String?,
    val price: Double,
    val outletId: String,
    val outletName: String,
    val category: String,
    val isVeg: Boolean,
    val isAvailable: Boolean,
    val prepTimeMinutes: Int,
    val rating: Double,
    val totalReviews: Int,
    val ingredients: String,    // JSON
    val tags: String,           // JSON
    val calories: Int?,
    val isPopular: Boolean,
    val isRecommended: Boolean,
    val isFavorite: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey val id: String,
    val foodItemId: String,
    val foodName: String,
    val foodImageUrl: String?,
    val outletId: String,
    val outletName: String,
    val price: Double,
    val quantity: Int,
    val selectedCustomizations: String,  // JSON
    val isVeg: Boolean,
    val specialInstructions: String?
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: String,
    val orderNumber: String,
    val userId: String,
    val vendorId: String,
    val outletId: String,
    val outletName: String,
    val items: String,          // JSON
    val subtotal: Double,
    val tax: Double,
    val total: Double,
    val status: String,
    val pickupSlot: String?,    // JSON nullable
    val estimatedPrepMinutes: Int,
    val actualPrepMinutes: Int?,
    val createdAt: String,
    val placedAt: String?,
    val acceptedAt: String?,
    val preparingAt: String?,
    val readyAt: String?,
    val pickedUpAt: String?,
    val cancelledAt: String?,
    val cancellationReason: String?,
    val paymentStatus: String,
    val paymentMethod: String,
    val specialInstructions: String?,
    val qrToken: String?,
    val cachedAt: Long = System.currentTimeMillis()
)
