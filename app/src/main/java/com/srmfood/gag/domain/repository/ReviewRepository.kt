package com.srmfood.gag.domain.repository

import com.srmfood.gag.domain.model.FoodReview
import com.srmfood.gag.domain.model.OutletReview
import com.srmfood.gag.domain.model.OutletReviewPage
import com.srmfood.gag.domain.model.OutletReviewReply
import com.srmfood.gag.domain.model.ReviewPage
import com.srmfood.gag.domain.model.ReviewReply

/**
 * ReviewRepository — domain interface for food item review operations.
 *
 * All operations that mutate data (INSERT/UPDATE/DELETE) are enforced
 * server-side by RLS + PostgreSQL trigger validation.  The repository
 * is responsible only for serialization and transport; security is NOT
 * delegated to the Android layer.
 *
 * Backed by Supabase PostgREST (direct table access) and RPCs defined
 * in migration 018_food_reviews.sql.
 */
interface ReviewRepository {

    // ─── Student — Reading Reviews ────────────────────────────────────────────

    /**
     * Fetches a paginated list of visible reviews for a food item.
     * Calls the get_food_reviews() RPC.
     *
     * @param foodItemId  UUID of the food item.
     * @param limit       Maximum rows to return (default 20).
     * @param offset      Zero-based pagination offset (default 0).
     */
    suspend fun getFoodReviews(
        foodItemId: String,
        limit: Int = 20,
        offset: Int = 0
    ): Result<ReviewPage>

    /**
     * Returns the calling student's review for a specific order item, or null
     * if they have not yet reviewed it.
     *
     * Calls the get_review_for_order_item() RPC.
     * Used by the order detail screen to show "already reviewed" state.
     *
     * @param orderItemId  UUID of the order item.
     */
    suspend fun getReviewForOrderItem(orderItemId: String): Result<FoodReview?>

    // ─── Student — Mutating Reviews ───────────────────────────────────────────

    /**
     * Creates a new food item review for a purchased order item.
     *
     * The server enforces:
     *   1. Caller == studentId
     *   2. Order belongs to the caller
     *   3. Order status == PICKED_UP
     *   4. OrderItem belongs to the order
     *   5. OrderItem.food_item_id == foodItemId
     *   6. No existing review for this order_item (UNIQUE constraint)
     *
     * @param studentId    The authenticated student's UUID (auth.uid()).
     * @param foodItemId   The food item being reviewed.
     * @param orderId      The PICKED_UP order containing the item.
     * @param orderItemId  The specific order item line.
     * @param rating       Star rating 1–5.
     * @param reviewText   Optional review body (max 2000 chars).
     */
    suspend fun createReview(
        studentId: String,
        foodItemId: String,
        orderId: String,
        orderItemId: String,
        rating: Int,
        reviewText: String?
    ): Result<FoodReview>

    /**
     * Updates the calling student's own review.
     *
     * Only [rating] and [reviewText] may be changed.
     * The server prevents changing student_id, food_item_id, order_id,
     * order_item_id, or is_visible via the RLS WITH CHECK clause.
     *
     * @param reviewId    UUID of the review to update.
     * @param rating      New star rating 1–5.
     * @param reviewText  New review body (max 2000 chars, or null to clear).
     */
    suspend fun updateReview(
        reviewId: String,
        rating: Int,
        reviewText: String?
    ): Result<FoodReview>

    /**
     * Deletes the calling student's own review.
     * The rating aggregation trigger will automatically recalculate the
     * food item's rating after deletion.
     *
     * @param reviewId  UUID of the review to delete.
     */
    suspend fun deleteReview(reviewId: String): Result<Unit>

    // ─── Vendor Operations ────────────────────────────────────────────────────

    /**
     * Fetches paginated reviews for food items owned by the calling vendor.
     * Calls the get_vendor_reviews() RPC.
     *
     * The RPC enforces vendor identity — vendors NEVER see another vendor's reviews.
     *
     * @param limit   Maximum rows (default 50).
     * @param offset  Zero-based pagination offset (default 0).
     */
    suspend fun getVendorReviews(
        limit: Int = 50,
        offset: Int = 0
    ): Result<ReviewPage>

    /**
     * Creates a vendor reply to a student review.
     *
     * The server enforces:
     *   - Caller is a VENDOR
     *   - Caller == vendorId
     *   - The reviewed food item belongs to the vendor's outlet
     *   - Only one reply per review (UNIQUE constraint)
     *
     * @param reviewId  UUID of the review to reply to.
     * @param vendorId  The authenticated vendor's UUID (auth.uid()).
     * @param replyText Reply body (1–1000 chars).
     */
    suspend fun createVendorReply(
        reviewId: String,
        vendorId: String,
        replyText: String
    ): Result<ReviewReply>

    /**
     * Updates the calling vendor's existing reply.
     *
     * The server prevents changing vendor_id or review_id on UPDATE.
     *
     * @param replyId   UUID of the reply to update.
     * @param replyText New reply body (1–1000 chars).
     */
    suspend fun updateVendorReply(
        replyId: String,
        replyText: String
    ): Result<ReviewReply>

    /**
     * Deletes the calling vendor's own reply.
     *
     * @param replyId   UUID of the reply to delete.
     */
    suspend fun deleteVendorReply(replyId: String): Result<Unit>

    // ─── Management / Admin Operations ───────────────────────────────────────

    /**
     * Fetches ALL reviews including hidden ones, for management overview.
     * Calls the get_management_reviews() RPC.
     *
     * Requires ADMIN role; server rejects non-admin callers.
     *
     * @param limit   Maximum rows (default 50).
     * @param offset  Zero-based pagination offset (default 0).
     */
    suspend fun getManagementReviews(
        limit: Int = 50,
        offset: Int = 0
    ): Result<ReviewPage>

    /**
     * Updates the visibility of a review.
     * Only admins can change a review's visibility.
     *
     * @param reviewId  UUID of the review.
     * @param isVisible true to make visible, false to hide.
     */
    suspend fun setReviewVisibility(
        reviewId: String,
        isVisible: Boolean
    ): Result<Unit>

    // ─── Outlet Reviews ───────────────────────────────────────────────────────

    /**
     * Submits a review for an outlet associated with a completed order.
     */
    suspend fun submitOutletReview(
        outletId: String,
        orderId: String,
        rating: Int,
        reviewText: String?
    ): Result<OutletReview>

    /**
     * Updates an existing outlet review.
     */
    suspend fun updateOutletReview(
        reviewId: String,
        rating: Int,
        reviewText: String?
    ): Result<OutletReview>

    /**
     * Deletes the calling student's own outlet review.
     */
    suspend fun deleteOutletReview(reviewId: String): Result<Unit>

    /**
     * Fetches all visible outlet reviews for a specific outlet.
     */
    suspend fun getOutletReviews(
        outletId: String,
        limit: Int = 20,
        offset: Int = 0
    ): Result<OutletReviewPage>

    /**
     * Fetches the student's outlet review for a specific order.
     */
    suspend fun getOutletReviewForOrder(orderId: String): Result<OutletReview?>

    /**
     * Submits a vendor reply to an outlet review.
     */
    suspend fun submitOutletVendorReply(
        reviewId: String,
        replyText: String
    ): Result<OutletReviewReply>

    /**
     * Updates a vendor's reply to an outlet review.
     */
    suspend fun updateOutletVendorReply(
        replyId: String,
        replyText: String
    ): Result<OutletReviewReply>

    /**
     * Deletes a vendor's reply to an outlet review.
     */
    suspend fun deleteOutletVendorReply(replyId: String): Result<Unit>

    /**
     * Fetches outlet reviews for all outlets owned by the vendor.
     */
    suspend fun getVendorOutletReviews(
        limit: Int = 50,
        offset: Int = 0
    ): Result<OutletReviewPage>

    /**
     * Fetches ALL outlet reviews for management overview.
     */
    suspend fun getManagementOutletReviews(
        limit: Int = 50,
        offset: Int = 0
    ): Result<OutletReviewPage>

    /**
     * Updates the visibility of an outlet review. (Admin only)
     */
    suspend fun setOutletReviewVisibility(
        reviewId: String,
        isVisible: Boolean
    ): Result<Unit>
}
