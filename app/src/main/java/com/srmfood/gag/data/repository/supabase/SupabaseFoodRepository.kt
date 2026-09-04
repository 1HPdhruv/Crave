package com.srmfood.gag.data.repository.supabase

import com.srmfood.gag.data.local.dao.FoodItemDao
import com.srmfood.gag.data.mapper.toDomain
import com.srmfood.gag.data.mapper.toEntity
import com.srmfood.gag.data.remote.dto.FoodItemDto
import com.srmfood.gag.data.remote.dto.FoodVariantDto
import com.srmfood.gag.data.remote.dto.FoodVariantOptionDto
import com.srmfood.gag.data.remote.dto.FoodCategoryDto
import com.srmfood.gag.domain.model.CustomizationOption
import com.srmfood.gag.domain.model.FoodCategory
import com.srmfood.gag.domain.model.FoodCustomization
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.model.FoodSearchFilter
import com.srmfood.gag.domain.repository.FoodRepository
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

// ─── Local DTOs only for this repository ─────────────────────────────────────

/** Minimal DTO to INSERT a row into favorites. */
@Serializable
private data class FavoriteInsert(
    @SerialName("user_id")      val userId: String,
    @SerialName("food_item_id") val foodItemId: String
)

/** DTO for reading a row from categories in Supabase. */
@Serializable
private data class CategoryDto(
    val id: String,
    val name: String,
    val emoji: String = "",
    val image_url: String? = null
)

// ─── Column selection string for food_items with variants ─────────────────────
// PostgREST embeds related rows when referenced with relationship syntax.
private const val FOOD_COLUMNS =
    "*, outlets(name), categories(name, emoji), food_variants(*, food_variant_options(*))"

// ─── Extension — map DB DTO → domain FoodItem ─────────────────────────────────
private fun FoodItemDto.toDomain(): FoodItem {
    return FoodItem(
        id             = id,
        name           = name,
        description    = description,
        imageUrl       = imageUrl,
        price          = price,
        outletId       = outletId,
        outletName     = outlet?.name ?: outletNameFlat ?: "",
        category       = category?.name ?: categoryNameFlat ?: "",
        isVeg          = isVeg ?: true,
        isAvailable    = isAvailable,
        prepTimeMinutes = prepTimeMinutes,
        rating         = rating,
        totalReviews   = totalReviews,
        ingredients    = ingredients ?: emptyList(),
        customizations = variants.map { variant ->
            FoodCustomization(
                id             = variant.id,
                name           = variant.name,
                isRequired     = variant.isRequired,
                maxSelections  = variant.maxSelections,
                options        = variant.options.map { opt ->
                    CustomizationOption(
                        id         = opt.id,
                        name       = opt.name,
                        extraPrice = opt.extraPrice
                    )
                }
            )
        },
        tags           = tags ?: emptyList(),
        calories       = calories,
        isPopular      = isPopular,
        isRecommended  = isRecommended,
        isFavorite     = false // set by caller after DB query
    )
}

// ─── Extension — map domain FoodItem → Room entity ────────────────────────────
private fun FoodItem.toEntity(): com.srmfood.gag.data.local.entity.FoodItemEntity {
    return com.srmfood.gag.data.local.entity.FoodItemEntity(
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
        ingredients     = Json.encodeToString(ingredients),
        tags            = Json.encodeToString(tags),
        calories        = calories,
        isPopular       = isPopular,
        isRecommended   = isRecommended,
        isFavorite      = isFavorite
    )
}

@Singleton
class SupabaseFoodRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: Auth,
    private val foodDao: FoodItemDao
) : FoodRepository {

    override suspend fun searchFood(filter: FoodSearchFilter): Result<List<FoodItem>> = runCatching {
        // Use the secure search_food() RPC which always enforces availability + active outlet.
        @Serializable
        data class SearchParams(
            @SerialName("p_query")          val query: String,
            @SerialName("p_outlet_id")      val outletId: String?,
            @SerialName("p_category")       val category: String?,
            @SerialName("p_is_veg")         val isVeg: Boolean?,
            @SerialName("p_max_price")      val maxPrice: Double?,
            @SerialName("p_available_only") val availableOnly: Boolean
        )

        // RPC returns a JSONB array; decode as list of FoodItemDto
        android.util.Log.d("SupabaseFoodRepo", "searchFood: Querying RPC 'search_food' with query='${filter.query}'")
        val dtos = postgrest.rpc(
            function = "search_food",
            parameters = SearchParams(
                query        = filter.query,
                outletId     = filter.outletId,
                category     = filter.category,
                isVeg        = filter.isVeg,
                maxPrice     = filter.maxPrice,
                availableOnly = filter.availableOnly
            )
        ).decodeList<FoodItemDto>()
        android.util.Log.d("SupabaseFoodRepo", "searchFood: Retrieved ${dtos.size} food items.")

        val favIds = getFavoriteIds()

        // Client-side sort for options not in RPC
        val sorted = when (filter.sortBy) {
            com.srmfood.gag.domain.model.SortOption.PRICE_LOW_TO_HIGH  -> dtos.sortedBy { it.price }
            com.srmfood.gag.domain.model.SortOption.PRICE_HIGH_TO_LOW  -> dtos.sortedByDescending { it.price }
            com.srmfood.gag.domain.model.SortOption.RATING             -> dtos.sortedByDescending { it.rating }
            com.srmfood.gag.domain.model.SortOption.PREP_TIME          -> dtos.sortedBy { it.prepTimeMinutes }
            com.srmfood.gag.domain.model.SortOption.RELEVANCE          -> dtos
        }
        sorted.map { it.toDomain().copy(isFavorite = it.id in favIds) }
    }.onFailure {
        android.util.Log.e("SupabaseFoodRepo", "searchFood: Failed to search catalogue", it)
    }

    override suspend fun getFoodById(foodId: String): Result<FoodItem> = runCatching {
        // We bypass local cache here because FoodItemEntity currently does not store nested variants.
        // Fetching directly guarantees the Food Detail screen gets full variant data and live availability.
        val dto = postgrest["food_items"]
            .select(Columns.raw(FOOD_COLUMNS)) {
                filter {
                    eq("id", foodId)
                }
            }
            .decodeSingle<FoodItemDto>()

        dto.toDomain().copy(isFavorite = foodId in getFavoriteIds())
    }

    override suspend fun getMenuByOutlet(outletId: String): Result<List<FoodItem>> = runCatching {
        android.util.Log.d("SupabaseFoodRepo", "getMenuByOutlet: Querying food_items for outlet=$outletId")
        val dtos = postgrest["food_items"]
            .select(Columns.raw(FOOD_COLUMNS)) {
                filter {
                    eq("outlet_id", outletId)
                }
            }
            .decodeList<FoodItemDto>()
        android.util.Log.d("SupabaseFoodRepo", "getMenuByOutlet: Retrieved ${dtos.size} food items.")

        val favIds = getFavoriteIds()
        val domains = dtos.map { it.toDomain().copy(isFavorite = it.id in favIds) }
        foodDao.insertAll(domains.map { it.toEntity() })
        domains
    }.onFailure {
        android.util.Log.e("SupabaseFoodRepo", "getMenuByOutlet: Failed to retrieve menu for outlet=$outletId", it)
    }

    override suspend fun getPopularFood(): Result<List<FoodItem>> = runCatching {
        val dtos = postgrest["food_items"]
            .select(Columns.raw(FOOD_COLUMNS)) {
                filter {
                    eq("is_popular", true)
                    eq("is_available", true)
                }
            }
            .decodeList<FoodItemDto>()
        val favIds = getFavoriteIds()
        dtos.map { it.toDomain().copy(isFavorite = it.id in favIds) }
    }

    override suspend fun getRecommendedFood(): Result<List<FoodItem>> = runCatching {
        val dtos = postgrest["food_items"]
            .select(Columns.raw(FOOD_COLUMNS)) {
                filter {
                    eq("is_recommended", true)
                    eq("is_available", true)
                }
            }
            .decodeList<FoodItemDto>()
        val favIds = getFavoriteIds()
        dtos.map { it.toDomain().copy(isFavorite = it.id in favIds) }
    }

    override suspend fun getAllFood(): Result<List<FoodItem>> = runCatching {
        android.util.Log.d("SupabaseFoodRepo", "getAllFood: Fetching all available food items")
        val dtos = postgrest["food_items"]
            .select(Columns.raw(FOOD_COLUMNS)) {
                filter { eq("is_available", true) }
            }
            .decodeList<FoodItemDto>()
        android.util.Log.d("SupabaseFoodRepo", "getAllFood: Retrieved ${dtos.size} items")
        val favIds = getFavoriteIds()
        dtos.map { it.toDomain().copy(isFavorite = it.id in favIds) }
    }

    override suspend fun getCategories(): Result<List<FoodCategory>> = runCatching {
        val dtos = postgrest["categories"].select().decodeList<CategoryDto>()
        dtos.map {
            FoodCategory(
                id       = it.id,
                name     = it.name,
                emoji    = it.emoji,
                imageUrl = it.image_url
            )
        }
    }

    override fun getFavorites(): Flow<List<FoodItem>> {
        return foodDao.observeFavorites().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun syncFavorites(): Result<Unit> = runCatching {
        val favIds = getFavoriteIds()
        if (favIds.isEmpty()) return@runCatching
        
        val dtos = postgrest["food_items"]
            .select(Columns.raw(FOOD_COLUMNS)) {
                filter { isIn("id", favIds.toList()) }
            }
            .decodeList<FoodItemDto>()
            
        val domains = dtos.map { it.toDomain().copy(isFavorite = true) }
        foodDao.insertAll(domains.map { it.toEntity() })
    }

    override suspend fun toggleFavorite(foodItemId: String): Result<Boolean> = runCatching {
        val current = foodDao.isFavorite(foodItemId) ?: false
        val newStatus = !current
        foodDao.updateFavorite(foodItemId, newStatus)

        // Sync to Supabase favorites table
        val userId = auth.currentSessionOrNull()?.user?.id
        if (userId != null) {
            if (newStatus) {
                // Insert into favorites — ignore conflict if already exists
                postgrest["favorites"].upsert(
                    FavoriteInsert(userId = userId, foodItemId = foodItemId)
                )
            } else {
                // Remove from favorites
                postgrest["favorites"].delete {
                    filter {
                        eq("user_id", userId)
                        eq("food_item_id", foodItemId)
                    }
                }
            }
        }
        newStatus
    }

    override suspend fun isFavorite(foodItemId: String): Boolean {
        return foodDao.isFavorite(foodItemId) ?: false
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Fetches the set of food_item_ids the current user has favorited from Supabase.
     * Falls back to empty set if not logged in.
     */
    private suspend fun getFavoriteIds(): Set<String> {
        val userId = auth.currentSessionOrNull()?.user?.id ?: return emptySet()
        return try {
            @Serializable
            data class FavRow(@SerialName("food_item_id") val foodItemId: String)
            postgrest["favorites"]
                .select(Columns.raw("food_item_id")) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<FavRow>()
                .map { it.foodItemId }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    // ─── Vendor Operations ────────────────────────────────────────────────────────

    override suspend fun getVendorFoodItems(): Result<List<FoodItem>> = runCatching {
        val userId = auth.currentSessionOrNull()?.user?.id ?: throw Exception("Not logged in")
        
        // 1. Get the vendor's outlet IDs
        @Serializable
        data class OutletIdDto(val id: String)
        val outletDtos = postgrest["outlets"].select(Columns.raw("id")) {
            filter { eq("vendor_id", userId) }
        }.decodeList<OutletIdDto>()
        
        val outletIds = outletDtos.map { it.id }
        if (outletIds.isEmpty()) return@runCatching emptyList()

        // 2. Fetch food items for those outlets
        val dtos = postgrest["food_items"].select(Columns.raw(FOOD_COLUMNS)) {
            filter { isIn("outlet_id", outletIds) }
        }.decodeList<FoodItemDto>()
        
        dtos.map { it.toDomain() }
    }

    override suspend fun updateFoodAvailability(foodId: String, isAvailable: Boolean): Result<Unit> = runCatching {
        postgrest["food_items"].update(
            { set("is_available", isAvailable) }
        ) {
            filter { eq("id", foodId) }
        }
    }

    override suspend fun updateFoodPrice(foodId: String, price: Double): Result<Unit> = runCatching {
        postgrest["food_items"].update(
            { set("price", price) }
        ) {
            filter { eq("id", foodId) }
        }
    }
}
