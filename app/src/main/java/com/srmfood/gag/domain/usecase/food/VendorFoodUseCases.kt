package com.srmfood.gag.domain.usecase.food

import com.srmfood.gag.domain.repository.FoodRepository
import javax.inject.Inject

class GetVendorFoodItemsUseCase @Inject constructor(private val foodRepository: FoodRepository) {
    suspend operator fun invoke() = foodRepository.getVendorFoodItems()
}

class UpdateFoodAvailabilityUseCase @Inject constructor(private val foodRepository: FoodRepository) {
    suspend operator fun invoke(foodId: String, isAvailable: Boolean) = foodRepository.updateFoodAvailability(foodId, isAvailable)
}

class UpdateFoodPriceUseCase @Inject constructor(private val foodRepository: FoodRepository) {
    suspend operator fun invoke(foodId: String, price: Double) = foodRepository.updateFoodPrice(foodId, price)
}
