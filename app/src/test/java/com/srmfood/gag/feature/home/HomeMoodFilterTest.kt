package com.srmfood.gag.feature.home

import com.srmfood.gag.domain.model.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeMoodFilterTest {
    private val foods = listOf(
        food("biryani", "Cozy paneer biryani", "Rice", isVeg = true),
        food("noodles", "Fiery chili noodles", "Noodles", tags = listOf("spicy")),
        food("salad", "Garden citrus salad", "Salad", isVeg = true)
    )

    @Test
    fun allMoodKeepsEveryFood() {
        assertEquals(listOf("biryani", "noodles", "salad"), filterForMood(foods, "All").map { it.id })
    }

    @Test
    fun plantMoodOnlyKeepsVegetarianFood() {
        assertEquals(listOf("biryani", "salad"), filterForMood(foods, "Plant").map { it.id })
    }

    @Test
    fun heatMoodMatchesDishTextAndTagsCaseInsensitively() {
        assertEquals(listOf("noodles"), filterForMood(foods, "Heat").map { it.id })
    }

    @Test
    fun comfortMoodMatchesComfortingDishTypes() {
        assertEquals(listOf("biryani", "noodles"), filterForMood(foods, "Comfort").map { it.id })
    }

    @Test
    fun brightMoodMatchesFreshDishTypes() {
        assertEquals(listOf("salad"), filterForMood(foods, "Bright").map { it.id })
    }

    private fun food(
        id: String,
        name: String,
        category: String,
        isVeg: Boolean = false,
        tags: List<String> = emptyList()
    ) = FoodItem(
        id = id,
        name = name,
        description = "",
        imageUrl = null,
        price = 100.0,
        outletId = "outlet-1",
        outletName = "Campus Kitchen",
        category = category,
        isVeg = isVeg,
        isAvailable = true,
        prepTimeMinutes = 10,
        rating = 4.5,
        totalReviews = 0,
        ingredients = emptyList(),
        customizations = emptyList(),
        tags = tags,
        calories = null,
        isPopular = true,
        isRecommended = false
    )
}
