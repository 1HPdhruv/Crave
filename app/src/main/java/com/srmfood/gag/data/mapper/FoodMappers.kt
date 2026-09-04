package com.srmfood.gag.data.mapper

import com.srmfood.gag.data.local.entity.FoodItemEntity
import com.srmfood.gag.data.remote.dto.CustomizationOptionDto
import com.srmfood.gag.data.remote.dto.FoodCustomizationDto
import com.srmfood.gag.data.remote.dto.FoodItemDto
import com.srmfood.gag.domain.model.CustomizationOption
import com.srmfood.gag.domain.model.FoodCustomization
import com.srmfood.gag.domain.model.FoodItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Maps FoodItemDto (from Supabase PostgREST) to FoodItem domain model.
 * outletName and category name come from nested PostgREST relationship expansions:
 *   outlets(name), categories(name, emoji), food_variants(*, food_variant_options(*))
 */
fun FoodItemDto.toDomain(): FoodItem = FoodItem(
    id              = id,
    name            = name,
    description     = description,
    imageUrl        = imageUrl,
    price           = price,
    outletId        = outletId,
    outletName      = outlet?.name ?: "",           // from outlets(name) join
    category        = category?.name ?: "",         // from categories(name) join
    isVeg           = isVeg ?: true,
    isAvailable     = isAvailable,
    prepTimeMinutes = prepTimeMinutes,
    rating          = rating,
    totalReviews    = totalReviews,
    ingredients     = ingredients ?: emptyList(),
    customizations  = variants.map { variant ->     // from food_variants join
        FoodCustomization(
            id            = variant.id,
            name          = variant.name,
            isRequired    = variant.isRequired,
            maxSelections = variant.maxSelections,
            options       = variant.options.map { opt ->
                CustomizationOption(
                    id         = opt.id,
                    name       = opt.name,
                    extraPrice = opt.extraPrice
                )
            }
        )
    },
    tags            = tags ?: emptyList(),
    calories        = calories,
    isPopular       = isPopular,
    isRecommended   = isRecommended
)

fun FoodItemDto.toEntity(): FoodItemEntity = FoodItemEntity(
    id              = id,
    name            = name,
    description     = description,
    imageUrl        = imageUrl,
    price           = price,
    outletId        = outletId,
    outletName      = outlet?.name ?: "",
    category        = category?.name ?: "",
    isVeg           = isVeg ?: true,
    isAvailable     = isAvailable,
    prepTimeMinutes = prepTimeMinutes,
    rating          = rating,
    totalReviews    = totalReviews,
    ingredients     = Json.encodeToString(ingredients ?: emptyList()),
    tags            = Json.encodeToString(tags ?: emptyList()),
    calories        = calories,
    isPopular       = isPopular,
    isRecommended   = isRecommended
)

fun FoodItemEntity.toDomain(customizations: List<FoodCustomization> = emptyList()): FoodItem = FoodItem(
    id              = id,
    name            = name,
    description     = description,
    imageUrl        = imageUrl,
    price           = price,
    outletId        = outletId,
    outletName      = outletName,
    category        = category,
    isVeg           = isVeg,
    isAvailable     = isAvailable,
    prepTimeMinutes = prepTimeMinutes,
    rating          = rating,
    totalReviews    = totalReviews,
    ingredients     = try { Json.decodeFromString(ingredients) } catch (e: Exception) { emptyList() },
    // Room cache doesn't store nested variants — fetched fresh from Supabase when needed
    customizations  = customizations,
    tags            = try { Json.decodeFromString(tags) } catch (e: Exception) { emptyList() },
    calories        = calories,
    isPopular       = isPopular,
    isRecommended   = isRecommended,
    isFavorite      = isFavorite
)

fun FoodCustomizationDto.toDomain(): FoodCustomization = FoodCustomization(
    id            = id,
    name          = name,
    options       = options.map { it.toDomain() },
    isRequired    = isRequired,
    maxSelections = maxSelections
)

fun CustomizationOptionDto.toDomain(): CustomizationOption = CustomizationOption(
    id         = id,
    name       = name,
    extraPrice = extraPrice
)

fun com.srmfood.gag.data.remote.dto.FoodCategoryDto.toDomain(): com.srmfood.gag.domain.model.FoodCategory =
    com.srmfood.gag.domain.model.FoodCategory(
        id       = id,
        name     = name,
        emoji    = emoji,
        imageUrl = imageUrl
    )
