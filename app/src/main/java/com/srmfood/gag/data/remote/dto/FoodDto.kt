package com.srmfood.gag.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Matches the food_items table row + PostgREST relationship expansions:
 *   food_items(*), outlets(name), categories(name, emoji), food_variants(*,food_variant_options(*))
 */
@Serializable
data class FoodItemDto(
    @SerialName("id")                val id: String,
    @SerialName("name")              val name: String,
    @SerialName("description")       val description: String = "",
    @SerialName("image_url")         val imageUrl: String? = null,
    @SerialName("price")             val price: Double,
    @SerialName("outlet_id")         val outletId: String,
    @SerialName("category_id")       val categoryId: String = "",
    @SerialName("is_veg")            val isVeg: Boolean? = true,
    @SerialName("is_available")      val isAvailable: Boolean = true,
    @SerialName("prep_time_minutes") val prepTimeMinutes: Int = 10,
    @SerialName("rating")            val rating: Double = 0.0,
    @SerialName("total_reviews")     val totalReviews: Int = 0,
    @SerialName("ingredients")       val ingredients: List<String>? = emptyList(),
    @SerialName("tags")              val tags: List<String>? = emptyList(),
    @SerialName("calories")          val calories: Int? = null,
    @SerialName("is_popular")        val isPopular: Boolean = false,
    @SerialName("is_recommended")    val isRecommended: Boolean = false,
    // PostgREST nested relationships (present when selected with Columns.raw)
    @SerialName("outlets")           val outlet: OutletNameDto? = null,
    @SerialName("categories")        val category: CategoryNameDto? = null,
    @SerialName("food_variants")     val variants: List<FoodVariantDto> = emptyList(),
    // Flat fields returned by search_food RPC
    @SerialName("outlet_name")       val outletNameFlat: String? = null,
    @SerialName("category_name")     val categoryNameFlat: String? = null
)

/** Lightweight outlet relation — only name is needed in menu listings. */
@Serializable
data class OutletNameDto(
    @SerialName("name") val name: String
)

/** Lightweight category relation — name + emoji for UI chips. */
@Serializable
data class CategoryNameDto(
    @SerialName("name")  val name: String,
    @SerialName("emoji") val emoji: String = ""
)

/** food_variants row — represents a customization group (e.g. "Size", "Spice Level"). */
@Serializable
data class FoodVariantDto(
    @SerialName("id")             val id: String,
    @SerialName("food_item_id")   val foodItemId: String,
    @SerialName("name")           val name: String,         // e.g. "Size"
    @SerialName("is_required")    val isRequired: Boolean = false,
    @SerialName("max_selections") val maxSelections: Int = 1,
    @SerialName("food_variant_options") val options: List<FoodVariantOptionDto> = emptyList()
)

/** food_variant_options row — one choice within a variant group. */
@Serializable
data class FoodVariantOptionDto(
    @SerialName("id")          val id: String,
    @SerialName("variant_id")  val variantId: String,
    @SerialName("name")        val name: String,            // e.g. "Large"
    @SerialName("extra_price") val extraPrice: Double = 0.0 // price delta from base price
)

@Serializable
data class FoodCustomizationDto(
    @SerialName("id")             val id: String,
    @SerialName("name")           val name: String,
    @SerialName("options")        val options: List<CustomizationOptionDto>,
    @SerialName("is_required")    val isRequired: Boolean = false,
    @SerialName("max_selections") val maxSelections: Int = 1
)

@Serializable
data class CustomizationOptionDto(
    @SerialName("id")          val id: String,
    @SerialName("name")        val name: String,
    @SerialName("extra_price") val extraPrice: Double = 0.0
)

@Serializable
data class FoodCategoryDto(
    @SerialName("id")        val id: String,
    @SerialName("name")      val name: String,
    @SerialName("emoji")     val emoji: String = "",
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class FoodSearchRequestDto(
    @SerialName("query")          val query: String,
    @SerialName("category")       val category: String? = null,
    @SerialName("outlet_id")      val outletId: String? = null,
    @SerialName("is_veg")         val isVeg: Boolean? = null,
    @SerialName("max_price")      val maxPrice: Double? = null,
    @SerialName("min_rating")     val minRating: Double? = null,
    @SerialName("max_prep_time")  val maxPrepTime: Int? = null,
    @SerialName("available_only") val availableOnly: Boolean = true,
    @SerialName("sort_by")        val sortBy: String = "relevance"
)
