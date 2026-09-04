package com.srmfood.gag.data.repository

import com.srmfood.gag.data.local.dao.FoodItemDao
import com.srmfood.gag.data.mapper.toDomain
import com.srmfood.gag.data.mapper.toEntity
import com.srmfood.gag.data.remote.api.FoodApi
import com.srmfood.gag.data.remote.dto.FoodSearchRequestDto
import com.srmfood.gag.domain.model.FoodCategory
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.model.FoodSearchFilter
import com.srmfood.gag.domain.repository.FoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockFoodRepository @Inject constructor(
    private val api: FoodApi,
    private val foodDao: FoodItemDao
) : FoodRepository {

    override suspend fun searchFood(filter: FoodSearchFilter): Result<List<FoodItem>> = runCatching {
        val response = api.searchFood(
            query = filter.query,
            category = filter.category,
            outletId = filter.outletId,
            isVeg = filter.isVeg,
            maxPrice = filter.maxPrice,
            minRating = filter.minRating,
            maxPrepTime = filter.maxPrepTimeMinutes,
            availableOnly = filter.availableOnly,
            sortBy = filter.sortBy.name.lowercase()
        )
        response.map { it.toDomain() }
    }

    override suspend fun getFoodById(foodId: String): Result<FoodItem> = runCatching {
        // Try local cache first
        val local = foodDao.getFoodById(foodId)
        if (local != null) return@runCatching local.toDomain()

        val response = api.getFoodById(foodId)
        foodDao.insertAll(listOf(response.toEntity()))
        response.toDomain()
    }

    override suspend fun getMenuByOutlet(outletId: String): Result<List<FoodItem>> = runCatching {
        val response = api.getMenuByOutlet(outletId)
        val entities = response.map { it.toEntity() }
        foodDao.insertAll(entities)
        response.map { it.toDomain() }
    }

    override suspend fun getPopularFood(): Result<List<FoodItem>> = runCatching {
        val response = api.getPopularFood()
        response.map { it.toDomain() }
    }

    override suspend fun getRecommendedFood(): Result<List<FoodItem>> = runCatching {
        val response = api.getRecommendedFood()
        response.map { it.toDomain() }
    }

    override suspend fun getAllFood(): Result<List<FoodItem>> = runCatching {
        val response = api.getPopularFood() // Mock: reuse popular endpoint as fallback
        response.map { it.toDomain() }
    }

    override suspend fun getCategories(): Result<List<FoodCategory>> = runCatching {
        val response = api.getCategories()
        response.map { it.toDomain() }
    }

    override fun getFavorites(): Flow<List<FoodItem>> {
        return foodDao.observeFavorites().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun syncFavorites(): Result<Unit> = Result.success(Unit)

    override suspend fun toggleFavorite(foodItemId: String): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun isFavorite(foodItemId: String): Boolean {
        return false
    }

    override suspend fun getVendorFoodItems(): Result<List<FoodItem>> {
        return Result.success(emptyList())
    }

    override suspend fun updateFoodAvailability(foodId: String, isAvailable: Boolean): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun updateFoodPrice(foodId: String, price: Double): Result<Unit> {
        return Result.success(Unit)
    }
}
