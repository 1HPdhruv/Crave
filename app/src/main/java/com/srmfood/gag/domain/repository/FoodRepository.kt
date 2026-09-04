package com.srmfood.gag.domain.repository

import com.srmfood.gag.domain.model.FoodCategory
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.model.FoodSearchFilter
import kotlinx.coroutines.flow.Flow

interface FoodRepository {
    suspend fun searchFood(filter: FoodSearchFilter): Result<List<FoodItem>>
    suspend fun getFoodById(foodId: String): Result<FoodItem>
    suspend fun getMenuByOutlet(outletId: String): Result<List<FoodItem>>
    suspend fun getPopularFood(): Result<List<FoodItem>>
    suspend fun getRecommendedFood(): Result<List<FoodItem>>
    suspend fun getAllFood(): Result<List<FoodItem>>
    suspend fun getCategories(): Result<List<FoodCategory>>
    fun getFavorites(): Flow<List<FoodItem>>
    suspend fun syncFavorites(): Result<Unit>
    suspend fun toggleFavorite(foodItemId: String): Result<Boolean>
    suspend fun isFavorite(foodItemId: String): Boolean
    
    // Vendor
    suspend fun getVendorFoodItems(): Result<List<FoodItem>>
    suspend fun updateFoodAvailability(foodId: String, isAvailable: Boolean): Result<Unit>
    suspend fun updateFoodPrice(foodId: String, price: Double): Result<Unit>
}
