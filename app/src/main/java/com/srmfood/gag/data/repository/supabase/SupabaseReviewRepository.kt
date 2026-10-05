package com.srmfood.gag.data.repository.supabase

import com.srmfood.gag.data.remote.dto.CreateReplyPayload
import com.srmfood.gag.data.remote.dto.CreateReviewPayload
import com.srmfood.gag.data.remote.dto.GetFoodReviewsParams
import com.srmfood.gag.data.remote.dto.GetPaginatedParams
import com.srmfood.gag.data.remote.dto.GetReviewForOrderItemParams
import com.srmfood.gag.data.remote.dto.ReviewDto
import com.srmfood.gag.data.remote.dto.ReviewPageDto
import com.srmfood.gag.data.remote.dto.ReviewReplyDto
import com.srmfood.gag.data.remote.dto.UpdateReplyPayload
import com.srmfood.gag.data.remote.dto.UpdateReviewPayload
import com.srmfood.gag.data.remote.dto.UpdateReviewVisibilityPayload
import com.srmfood.gag.data.remote.dto.*
import com.srmfood.gag.data.remote.dto.*
import com.srmfood.gag.domain.model.FoodReview
import com.srmfood.gag.domain.model.OutletReview
import com.srmfood.gag.domain.model.OutletReviewPage
import com.srmfood.gag.domain.model.OutletReviewReply
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.srmfood.gag.domain.model.ReviewPage
import com.srmfood.gag.domain.model.ReviewReply
import com.srmfood.gag.domain.repository.ReviewRepository
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SupabaseReviewRepository — concrete implementation of [ReviewRepository].
 *
 * Security model:
 *   INSERT reviews       → purchase validation trigger (server)
 *   UPDATE/DELETE reviews → RLS policy (server)
 *   INSERT review_replies → vendor ownership trigger (server)
 *   UPDATE review_replies → ownership trigger (server)
 *   All RPCs             → SECURITY DEFINER + role checks (server)
 *
 * The Android layer does NOT perform purchase or ownership validation.
 * Any attempt to bypass the UI and call Supabase directly will be rejected
 * by the database triggers and RLS policies.
 */
@Singleton
class SupabaseReviewRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: Auth
) : ReviewRepository {

    // ─── Student — Reading Reviews ────────────────────────────────────────────

    override suspend fun getFoodReviews(
        foodItemId: String,
        limit: Int,
        offset: Int
    ): Result<ReviewPage> = runCatching {
        val page = postgrest.rpc(
            function   = "get_food_reviews",
            parameters = GetFoodReviewsParams(
                foodItemId = foodItemId,
                limit      = limit,
                offset     = offset
            )
        ).decodeAs<ReviewPageDto>()

        page.toDomain()
    }.onFailure { e ->
        android.util.Log.e(TAG, "getFoodReviews($foodItemId) failed", e)
    }

    override suspend fun getReviewForOrderItem(orderItemId: String): Result<FoodReview?> =
        runCatching {
            // The RPC returns the JSON object or SQL NULL (which becomes the JSON literal 'null').
            // decodeAs<T?> triggers a generic bound issue on some Supabase SDK versions;
            // we decode as non-null and treat SerializationException as "not found".
            val raw = postgrest.rpc(
                function   = "get_review_for_order_item",
                parameters = GetReviewForOrderItemParams(orderItemId = orderItemId)
            ).data
            // Supabase returns "null" string literal when the RPC returns SQL NULL
            if (raw.isNullOrBlank() || raw.trim() == "null") {
                null
            } else {
                kotlinx.serialization.json.Json.decodeFromString<ReviewDto>(raw).toDomain()
            }
        }.onFailure { e ->
            android.util.Log.e(TAG, "getReviewForOrderItem($orderItemId) failed", e)
        }

    // ─── Student — Mutating Reviews ───────────────────────────────────────────

    override suspend fun createReview(
        studentId: String,
        foodItemId: String,
        orderId: String,
        orderItemId: String,
        rating: Int,
        reviewText: String?
    ): Result<FoodReview> = runCatching {
        requireAuthenticated()

        val dto = postgrest["reviews"].insert(
            CreateReviewPayload(
                studentId   = studentId,
                foodItemId  = foodItemId,
                orderId     = orderId,
                orderItemId = orderItemId,
                rating      = rating,
                reviewText  = reviewText
            )
        ) {
            select()   // Return the inserted row
        }.decodeSingle<ReviewDto>()

        dto.toDomain()
    }.onFailure { e ->
        android.util.Log.e(TAG, "createReview failed for orderItem=$orderItemId", e)
    }

    override suspend fun updateReview(
        reviewId: String,
        rating: Int,
        reviewText: String?
    ): Result<FoodReview> = runCatching {
        requireAuthenticated()

        val dto = postgrest["reviews"].update(
            UpdateReviewPayload(rating = rating, reviewText = reviewText)
        ) {
            filter { eq("id", reviewId) }
            select()
        }.decodeSingle<ReviewDto>()

        dto.toDomain()
    }.onFailure { e ->
        android.util.Log.e(TAG, "updateReview($reviewId) failed", e)
    }

    override suspend fun deleteReview(reviewId: String): Result<Unit> = runCatching {
        requireAuthenticated()

        postgrest["reviews"].delete {
            filter { eq("id", reviewId) }
        }
        Unit  // Explicitly return Unit; delete() returns PostgrestResult
    }.onFailure { e ->
        android.util.Log.e(TAG, "deleteReview($reviewId) failed", e)
    }

    // ─── Vendor Operations ────────────────────────────────────────────────────

    override suspend fun getVendorReviews(
        limit: Int,
        offset: Int
    ): Result<ReviewPage> = runCatching {
        val page = postgrest.rpc(
            function   = "get_vendor_reviews",
            parameters = GetPaginatedParams(limit = limit, offset = offset)
        ).decodeAs<ReviewPageDto>()

        page.toDomain()
    }.onFailure { e ->
        android.util.Log.e(TAG, "getVendorReviews failed", e)
    }

    override suspend fun createVendorReply(
        reviewId: String,
        vendorId: String,
        replyText: String
    ): Result<ReviewReply> = runCatching {
        requireAuthenticated()

        val dto = postgrest["review_replies"].insert(
            CreateReplyPayload(
                reviewId  = reviewId,
                vendorId  = vendorId,
                replyText = replyText
            )
        ) {
            select()
        }.decodeSingle<ReviewReplyDto>()

        dto.toDomain()
    }.onFailure { e ->
        android.util.Log.e(TAG, "createVendorReply(reviewId=$reviewId) failed", e)
    }

    override suspend fun updateVendorReply(
        replyId: String,
        replyText: String
    ): Result<ReviewReply> = runCatching {
        requireAuthenticated()

        val dto = postgrest["review_replies"].update(
            UpdateReplyPayload(replyText = replyText)
        ) {
            filter { eq("id", replyId) }
            select()
        }.decodeSingle<ReviewReplyDto>()

        dto.toDomain()
    }.onFailure { e ->
        android.util.Log.e(TAG, "updateVendorReply($replyId) failed", e)
    }

    override suspend fun deleteVendorReply(replyId: String): Result<Unit> = runCatching {
        requireAuthenticated()

        postgrest["review_replies"].delete {
            filter { eq("id", replyId) }
        }
        Unit
    }.onFailure { e ->
        android.util.Log.e(TAG, "deleteVendorReply($replyId) failed", e)
    }

    // ─── Management / Admin Operations ───────────────────────────────────────

    override suspend fun getManagementReviews(
        limit: Int,
        offset: Int
    ): Result<ReviewPage> = runCatching {
        val page = postgrest.rpc(
            function   = "get_management_reviews",
            parameters = GetPaginatedParams(limit = limit, offset = offset)
        ).decodeAs<ReviewPageDto>()

        page.toDomain()
    }.onFailure { e ->
        android.util.Log.e(TAG, "getManagementReviews failed", e)
    }

    override suspend fun setReviewVisibility(
        reviewId: String,
        isVisible: Boolean
    ): Result<Unit> = runCatching {
        requireAuthenticated()
        
        postgrest["reviews"].update(
            UpdateReviewVisibilityPayload(isVisible = isVisible)
        ) {
            filter { eq("id", reviewId) }
        }
        Unit
    }.onFailure { e ->
        android.util.Log.e(TAG, "setReviewVisibility($reviewId) failed", e)
    }

    // ─── Outlet Reviews ───────────────────────────────────────────────────────

    override suspend fun submitOutletReview(
        outletId: String,
        orderId: String,
        rating: Int,
        reviewText: String?
    ): Result<OutletReview> = runCatching {
        val uid = auth.currentSessionOrNull()?.user?.id
            ?: throw IllegalStateException("Not authenticated")

        val payload = CreateOutletReviewPayload(
            studentId = uid,
            outletId = outletId,
            orderId = orderId,
            rating = rating,
            reviewText = reviewText
        )

        val dto = postgrest["outlet_reviews"].insert(payload) {
            select()
        }.decodeSingle<OutletReviewDto>()

        dto.toDomain()
    }.onFailure { e ->
        android.util.Log.e(TAG, "submitOutletReview failed", e)
    }

    override suspend fun updateOutletReview(
        reviewId: String,
        rating: Int,
        reviewText: String?
    ): Result<OutletReview> = runCatching {
        requireAuthenticated()
        
        val dto = postgrest["outlet_reviews"].update(
            UpdateOutletReviewPayload(
                rating = rating,
                reviewText = reviewText
            )
        ) {
            filter { eq("id", reviewId) }
            select()
        }.decodeSingle<OutletReviewDto>()

        dto.toDomain()
    }.onFailure { e ->
        android.util.Log.e(TAG, "updateOutletReview($reviewId) failed", e)
    }

    override suspend fun deleteOutletReview(reviewId: String): Result<Unit> = runCatching {
        requireAuthenticated()
        postgrest["outlet_reviews"].delete {
            filter { eq("id", reviewId) }
        }
        Unit
    }.onFailure { e ->
        android.util.Log.e(TAG, "deleteOutletReview($reviewId) failed", e)
    }

    override suspend fun getOutletReviews(
        outletId: String,
        limit: Int,
        offset: Int
    ): Result<OutletReviewPage> = runCatching {
        @Serializable
        data class Params(
            @SerialName("p_outlet_id") val outletId: String,
            @SerialName("p_limit") val limit: Int,
            @SerialName("p_offset") val offset: Int
        )

        val pageDto = postgrest.rpc(
            function = "get_outlet_reviews",
            parameters = Params(outletId, limit, offset)
        ).decodeAs<OutletReviewPageDto>()

        pageDto.toDomain()
    }.onFailure { e ->
        android.util.Log.e(TAG, "getOutletReviews($outletId) failed", e)
    }

    override suspend fun getOutletReviewForOrder(orderId: String): Result<OutletReview?> = runCatching {
        requireAuthenticated()
        @Serializable
        data class Params(@SerialName("p_order_id") val orderId: String)
        
        try {
            val dto = postgrest.rpc(
                function = "get_outlet_review_for_order",
                parameters = Params(orderId)
            ).decodeAs<OutletReviewDto>()
            dto.toDomain()
        } catch (e: Exception) {
            // Usually indicates no rows/not found when decoding single from JSONB, if not careful
            // We can also handle if it returns null explicitly depending on postgrest-kt behavior
            null
        }
    }.onFailure { e ->
        android.util.Log.e(TAG, "getOutletReviewForOrder($orderId) failed", e)
    }

    override suspend fun submitOutletVendorReply(
        reviewId: String,
        replyText: String
    ): Result<OutletReviewReply> = runCatching {
        val uid = auth.currentSessionOrNull()?.user?.id
            ?: throw IllegalStateException("Not authenticated")

        val payload = CreateOutletReplyPayload(
            outletReviewId = reviewId,
            vendorId = uid,
            replyText = replyText
        )

        val dto = postgrest["outlet_review_replies"].insert(payload) {
            select()
        }.decodeSingle<OutletReviewReplyDto>()

        dto.toDomain()
    }.onFailure { e ->
        android.util.Log.e(TAG, "submitOutletVendorReply failed", e)
    }

    override suspend fun updateOutletVendorReply(
        replyId: String,
        replyText: String
    ): Result<OutletReviewReply> = runCatching {
        requireAuthenticated()

        val dto = postgrest["outlet_review_replies"].update(
            UpdateOutletReplyPayload(replyText = replyText)
        ) {
            filter { eq("id", replyId) }
            select()
        }.decodeSingle<OutletReviewReplyDto>()

        dto.toDomain()
    }.onFailure { e ->
        android.util.Log.e(TAG, "updateOutletVendorReply($replyId) failed", e)
    }

    override suspend fun deleteOutletVendorReply(replyId: String): Result<Unit> = runCatching {
        requireAuthenticated()

        postgrest["outlet_review_replies"].delete {
            filter { eq("id", replyId) }
        }
        Unit
    }.onFailure { e ->
        android.util.Log.e(TAG, "deleteOutletVendorReply($replyId) failed", e)
    }

    override suspend fun getVendorOutletReviews(
        limit: Int,
        offset: Int
    ): Result<OutletReviewPage> = runCatching {
        requireAuthenticated()

        val page = postgrest.rpc(
            function = "get_vendor_outlet_reviews",
            parameters = GetPaginatedParams(limit, offset)
        ).decodeAs<OutletReviewPageDto>()

        page.toDomain()
    }.onFailure { e ->
        android.util.Log.e(TAG, "getVendorOutletReviews failed", e)
    }

    override suspend fun getManagementOutletReviews(
        limit: Int,
        offset: Int
    ): Result<OutletReviewPage> = runCatching {
        val page = postgrest.rpc(
            function = "get_management_outlet_reviews",
            parameters = GetPaginatedParams(limit, offset)
        ).decodeAs<OutletReviewPageDto>()

        page.toDomain()
    }.onFailure { e ->
        android.util.Log.e(TAG, "getManagementOutletReviews failed", e)
    }

    override suspend fun setOutletReviewVisibility(
        reviewId: String,
        isVisible: Boolean
    ): Result<Unit> = runCatching {
        requireAuthenticated()
        
        postgrest["outlet_reviews"].update(
            UpdateOutletReviewVisibilityPayload(isVisible = isVisible)
        ) {
            filter { eq("id", reviewId) }
        }
        Unit
    }.onFailure { e ->
        android.util.Log.e(TAG, "setOutletReviewVisibility($reviewId) failed", e)
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    private fun requireAuthenticated() {
        auth.currentSessionOrNull()
            ?: throw IllegalStateException("User must be authenticated to perform this operation.")
    }

    private companion object {
        const val TAG = "SupabaseReviewRepo"
    }
}

// ─── Extension mappers ────────────────────────────────────────────────────────

private fun ReviewPageDto.toDomain() = ReviewPage(
    reviews = reviews.map { it.toDomain() },
    total   = total
)

private fun ReviewDto.toDomain() = FoodReview(
    id           = id,
    studentId    = studentId,
    studentName  = studentName,
    foodItemId   = foodItemId,
    foodName     = foodName,
    orderId      = orderId,
    orderItemId  = orderItemId,
    rating       = rating,
    reviewText   = reviewText,
    isVisible    = isVisible,
    createdAt    = createdAt,
    updatedAt    = updatedAt,
    vendorReply  = vendorReply?.toDomain()
)

private fun ReviewReplyDto.toDomain() = ReviewReply(
    id         = id,
    reviewId   = reviewId,
    vendorId   = vendorId,
    replyText  = replyText,
    createdAt  = createdAt,
    updatedAt  = updatedAt
)
