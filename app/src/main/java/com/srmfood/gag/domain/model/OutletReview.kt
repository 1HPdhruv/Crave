package com.srmfood.gag.domain.model

data class OutletReview(
    val id: String,
    val studentId: String,
    val studentName: String,
    val outletId: String,
    val outletName: String? = null,
    val orderId: String,
    val rating: Int,
    val reviewText: String? = null,
    val isVisible: Boolean,
    val createdAt: String,
    val vendorReply: OutletReviewReply? = null
)

data class OutletReviewReply(
    val id: String,
    val vendorId: String? = null,
    val replyText: String,
    val createdAt: String
)

data class OutletReviewPage(
    val reviews: List<OutletReview>,
    val total: Int
)
