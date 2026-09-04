package com.srmfood.gag.domain.model

import kotlinx.serialization.Serializable

/**
 * FoodItem domain model — represents a food item on an outlet's menu.
 */
@Serializable
data class FoodItem(
    val id: String,
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
    val ingredients: List<String>,
    val customizations: List<FoodCustomization>,
    val tags: List<String>,
    val calories: Int?,
    val isPopular: Boolean,
    val isRecommended: Boolean,
    val isFavorite: Boolean = false
)

@Serializable
data class FoodCustomization(
    val id: String,
    val name: String,           // e.g., "Spice Level"
    val options: List<CustomizationOption>,
    val isRequired: Boolean,
    val maxSelections: Int = 1
)

@Serializable
data class CustomizationOption(
    val id: String,
    val name: String,           // e.g., "Mild", "Medium", "Hot"
    val extraPrice: Double = 0.0
)

@Serializable
data class FoodCategory(
    val id: String,
    val name: String,
    val emoji: String,
    val imageUrl: String?
)

/** Search filter parameters */
data class FoodSearchFilter(
    val query: String = "",
    val category: String? = null,
    val outletId: String? = null,
    val isVeg: Boolean? = null,
    val maxPrice: Double? = null,
    val minRating: Double? = null,
    val maxPrepTimeMinutes: Int? = null,
    val availableOnly: Boolean = true,
    val sortBy: SortOption = SortOption.RELEVANCE
)

@Serializable
enum class SortOption {
    RELEVANCE, PRICE_LOW_TO_HIGH, PRICE_HIGH_TO_LOW, RATING, PREP_TIME
}
