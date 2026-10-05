package com.srmfood.gag.data.remote.dto

import com.srmfood.gag.domain.model.OutletReview
import com.srmfood.gag.domain.model.OutletReviewPage
import com.srmfood.gag.domain.model.OutletReviewReply
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OutletReviewDto(
    @SerialName("id")            val id: String,
    @SerialName("student_id")    val studentId: String,
    @SerialName("student_name")  val studentName: String? = null,
    @SerialName("outlet_id")     val outletId: String,
    @SerialName("outlet_name")   val outletName: String? = null,
    @SerialName("order_id")      val orderId: String,
    @SerialName("rating")        val rating: Int,
    @SerialName("review_text")   val reviewText: String? = null,
    @SerialName("is_visible")    val isVisible: Boolean,
    @SerialName("created_at")    val createdAt: String,
    @SerialName("updated_at")    val updatedAt: String? = null,
    @SerialName("vendor_reply")  val vendorReply: OutletReviewReplyDto? = null
) {
    fun toDomain() = OutletReview(
        id = id,
        studentId = studentId,
        studentName = studentName ?: "Unknown Student",
        outletId = outletId,
        outletName = outletName,
        orderId = orderId,
        rating = rating,
        reviewText = reviewText,
        isVisible = isVisible,
        createdAt = createdAt.take(10), // simplified date
        vendorReply = vendorReply?.toDomain()
    )
}

@Serializable
data class OutletReviewReplyDto(
    @SerialName("id")         val id: String,
    @SerialName("vendor_id")  val vendorId: String? = null,
    @SerialName("reply_text") val replyText: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toDomain() = OutletReviewReply(
        id = id,
        vendorId = vendorId,
        replyText = replyText,
        createdAt = createdAt.take(10)
    )
}

@Serializable
data class OutletReviewPageDto(
    @SerialName("reviews") val reviews: List<OutletReviewDto>? = null,
    @SerialName("total")   val total: Int
) {
    fun toDomain() = OutletReviewPage(
        reviews = reviews?.map { it.toDomain() } ?: emptyList(),
        total = total
    )
}

@Serializable
data class CreateOutletReviewPayload(
    @SerialName("student_id")    val studentId: String,
    @SerialName("outlet_id")     val outletId: String,
    @SerialName("order_id")      val orderId: String,
    @SerialName("rating")        val rating: Int,
    @SerialName("review_text")   val reviewText: String? = null
)

@Serializable
data class UpdateOutletReviewPayload(
    @SerialName("rating")      val rating: Int,
    @SerialName("review_text") val reviewText: String? = null
)

@Serializable
data class CreateOutletReplyPayload(
    @SerialName("outlet_review_id") val outletReviewId: String,
    @SerialName("vendor_id")        val vendorId: String,
    @SerialName("reply_text")       val replyText: String
)

@Serializable
data class UpdateOutletReplyPayload(
    @SerialName("reply_text") val replyText: String
)

@Serializable
data class UpdateOutletReviewVisibilityPayload(
    @SerialName("is_visible") val isVisible: Boolean
)
