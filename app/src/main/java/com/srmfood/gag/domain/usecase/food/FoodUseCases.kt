package com.srmfood.gag.domain.usecase.food

import com.srmfood.gag.domain.model.FoodCategory
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.model.FoodSearchFilter
import com.srmfood.gag.domain.repository.FoodRepository
import javax.inject.Inject

class SearchFoodUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    suspend operator fun invoke(filter: FoodSearchFilter): Result<List<FoodItem>> {
        if (filter.query.isBlank() && filter.category == null && filter.outletId == null) {
            return Result.failure(IllegalArgumentException("Search query or filter required"))
        }
        return foodRepository.searchFood(filter)
    }
}

class GetFoodItemUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    suspend operator fun invoke(foodId: String): Result<FoodItem> {
        if (foodId.isBlank()) return Result.failure(IllegalArgumentException("Food ID required"))
        return foodRepository.getFoodById(foodId)
    }
}

class GetMenuByOutletUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    suspend operator fun invoke(outletId: String): Result<List<FoodItem>> =
        foodRepository.getMenuByOutlet(outletId)
}

class GetPopularFoodUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    suspend operator fun invoke(): Result<List<FoodItem>> = foodRepository.getPopularFood()
}

class GetRecommendedFoodUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    suspend operator fun invoke(): Result<List<FoodItem>> = foodRepository.getRecommendedFood()
}

class GetCategoriesUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    suspend operator fun invoke(): Result<List<FoodCategory>> = foodRepository.getCategories()
}

/**
 * Fetches all available food items without any is_popular/is_recommended filter.
 * Used as a fallback when those flags are not set in the database.
 */
class GetAllFoodUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    suspend operator fun invoke(): Result<List<FoodItem>> = foodRepository.getAllFood()
}

class ToggleFavoriteUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    suspend operator fun invoke(foodItemId: String): Result<Boolean> =
        foodRepository.toggleFavorite(foodItemId)
}

class GetFavoritesUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    operator fun invoke() = foodRepository.getFavorites()
}

class RefreshFavoritesUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    suspend operator fun invoke(): Result<Unit> = foodRepository.syncFavorites()
}
