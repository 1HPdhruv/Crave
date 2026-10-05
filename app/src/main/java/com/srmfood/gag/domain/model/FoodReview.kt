package com.srmfood.gag.domain.model

/**
 * FoodReview domain model.
 *
 * Represents a genuine-purchase food item review submitted by a student
 * after an order has reached PICKED_UP status.
 *
 * Relationship:
 *   Student → Order (PICKED_UP) → OrderItem → FoodItem → FoodReview
 *
 * The [orderId] and [orderItemId] fields are retained in the domain model
 * so the app can:
 *   - Show the "already reviewed" state on the order detail screen
 *   - Prevent the student from attempting to re-review the same item
 */
data class FoodReview(
    val id: String,

    /** The student who authored this review. */
    val studentId: String,

    /** Display name of the student — populated from the profiles join. */
    val studentName: String,

    /** The food item being reviewed. */
    val foodItemId: String,

    /** The food item name. */
    val foodName: String? = null,

    /** The completed order this review is tied to. */
    val orderId: String,

    /** The specific order item line — uniqueness anchor (UNIQUE constraint). */
    val orderItemId: String,

    /** Star rating: 1 (lowest) to 5 (highest). */
    val rating: Int,

    /** Optional review body text (max 2000 chars). */
    val reviewText: String?,

    /**
     * Visibility flag.
     * true  = visible to all users (default)
     * false = hidden (future moderation; only admin can flip)
     */
    val isVisible: Boolean = true,

    val createdAt: String,
    val updatedAt: String,

    /**
     * Vendor reply, if one exists.
     * Null means no reply has been posted yet.
     */
    val vendorReply: ReviewReply?
)

/**
 * ReviewReply domain model.
 *
 * A vendor's single reply to a [FoodReview].
 * The uniqueness constraint (UNIQUE(review_id)) is enforced at the database level.
 *
 * Vendor ownership is validated server-side via:
 *   review → food_item → outlet → vendor_id == auth.uid()
 */
data class ReviewReply(
    val id: String,

    /** The review this reply belongs to. */
    val reviewId: String,

    /** The vendor who wrote this reply. */
    val vendorId: String,

    /** The reply content (1 – 1000 chars). */
    val replyText: String,

    val createdAt: String,
    val updatedAt: String
)

/**
 * Page of reviews returned by get_food_reviews() RPC.
 */
data class ReviewPage(
    val reviews: List<FoodReview>,
    val total: Int
)
