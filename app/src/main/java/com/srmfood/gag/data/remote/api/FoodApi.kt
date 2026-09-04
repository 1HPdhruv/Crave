package com.srmfood.gag.data.remote.api

import com.srmfood.gag.data.remote.dto.FoodCategoryDto
import com.srmfood.gag.data.remote.dto.FoodItemDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface FoodApi {
    @GET("food/search")
    suspend fun searchFood(
        @Query("query") query: String,
        @Query("category") category: String? = null,
        @Query("outlet_id") outletId: String? = null,
        @Query("is_veg") isVeg: Boolean? = null,
        @Query("max_price") maxPrice: Double? = null,
        @Query("min_rating") minRating: Double? = null,
        @Query("max_prep_time") maxPrepTime: Int? = null,
        @Query("available_only") availableOnly: Boolean = true,
        @Query("sort_by") sortBy: String = "relevance"
    ): List<FoodItemDto>

    @GET("food/{id}")
    suspend fun getFoodById(@Path("id") id: String): FoodItemDto

    @GET("outlets/{outletId}/menu")
    suspend fun getMenuByOutlet(@Path("outletId") outletId: String): List<FoodItemDto>

    @GET("food/popular")
    suspend fun getPopularFood(): List<FoodItemDto>

    @GET("food/recommended")
    suspend fun getRecommendedFood(): List<FoodItemDto>

    @GET("food/categories")
    suspend fun getCategories(): List<FoodCategoryDto>

    @GET("favorites")
    suspend fun getFavorites(): List<FoodItemDto>

    @GET("favorites/{foodId}")
    suspend fun toggleFavorite(@Path("foodId") foodId: String): Map<String, Boolean>
}
