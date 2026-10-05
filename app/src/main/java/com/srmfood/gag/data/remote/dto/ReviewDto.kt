package com.srmfood.gag.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTO for a review row returned by:
 *   - get_food_reviews() RPC
 *   - get_review_for_order_item() RPC
 *   - get_vendor_reviews() RPC
 *   - get_management_reviews() RPC
 *
 * All fields are nullable with defaults to tolerate partial projections
 * (e.g., get_review_for_order_item does not return student_name).
 */
@Serializable
data class ReviewDto(
    @SerialName("id")            val id: String,
    @SerialName("student_id")    val studentId: String,
    @SerialName("student_name")  val studentName: String = "",
    @SerialName("food_item_id")  val foodItemId: String,
    @SerialName("food_name")     val foodName: String? = null,
    @SerialName("outlet_id")     val outletId: String? = null,
    @SerialName("outlet_name")   val outletName: String? = null,
    @SerialName("vendor_id")     val vendorId: String? = null,
    @SerialName("order_id")      val orderId: String,
    @SerialName("order_item_id") val orderItemId: String,
    @SerialName("rating")        val rating: Int,
    @SerialName("review_text")   val reviewText: String? = null,
    @SerialName("is_visible")    val isVisible: Boolean = true,
    @SerialName("created_at")    val createdAt: String = "",
    @SerialName("updated_at")    val updatedAt: String = "",
    @SerialName("vendor_reply")  val vendorReply: ReviewReplyDto? = null
)

/**
 * Wire DTO for a review_reply row.
 */
@Serializable
data class ReviewReplyDto(
    @SerialName("id")          val id: String,
    @SerialName("review_id")   val reviewId: String = "",
    @SerialName("vendor_id")   val vendorId: String = "",
    @SerialName("reply_text")  val replyText: String,
    @SerialName("created_at")  val createdAt: String = "",
    @SerialName("updated_at")  val updatedAt: String = ""
)

/**
 * RPC response wrapper from get_food_reviews() / get_vendor_reviews() /
 * get_management_reviews().
 */
@Serializable
data class ReviewPageDto(
    @SerialName("reviews") val reviews: List<ReviewDto> = emptyList(),
    @SerialName("total")   val total: Int = 0
)

// ─── INSERT payloads ──────────────────────────────────────────────────────────

/**
 * Payload for inserting a row into the reviews table directly via PostgREST.
 * The purchase validation trigger runs server-side before the row is committed.
 */
@Serializable
data class CreateReviewPayload(
    @SerialName("student_id")    val studentId: String,
    @SerialName("food_item_id")  val foodItemId: String,
    @SerialName("order_id")      val orderId: String,
    @SerialName("order_item_id") val orderItemId: String,
    @SerialName("rating")        val rating: Int,
    @SerialName("review_text")   val reviewText: String? = null
)

/**
 * Payload for updating a review row (only rating and review_text are mutable
 * by the student; the server's RLS WITH CHECK prevents changing other columns).
 */
@Serializable
data class UpdateReviewPayload(
    @SerialName("rating")      val rating: Int,
    @SerialName("review_text") val reviewText: String? = null
)

/**
 * Payload for updating a review's visibility (admin only).
 */
@Serializable
data class UpdateReviewVisibilityPayload(
    @SerialName("is_visible") val isVisible: Boolean
)

/**
 * Payload for inserting a vendor reply.
 */
@Serializable
data class CreateReplyPayload(
    @SerialName("review_id")  val reviewId: String,
    @SerialName("vendor_id")  val vendorId: String,
    @SerialName("reply_text") val replyText: String
)

/**
 * Payload for updating a vendor reply.
 */
@Serializable
data class UpdateReplyPayload(
    @SerialName("reply_text") val replyText: String
)

// ─── RPC parameter payloads ───────────────────────────────────────────────────

@Serializable
data class GetFoodReviewsParams(
    @SerialName("p_food_item_id") val foodItemId: String,
    @SerialName("p_limit")        val limit: Int = 20,
    @SerialName("p_offset")       val offset: Int = 0
)

@Serializable
data class GetReviewForOrderItemParams(
    @SerialName("p_order_item_id") val orderItemId: String
)

@Serializable
data class GetPaginatedParams(
    @SerialName("p_limit")  val limit: Int = 50,
    @SerialName("p_offset") val offset: Int = 0
)
