package com.srmfood.gag.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RazorpayOrderDetails(
    val razorpay_order_id: String,
    val amount: Int,
    val key_id: String,
    val currency: String = "INR"
)

@Serializable
data class PaymentVerificationRequest(
    val order_id: String,
    val razorpay_order_id: String,
    val razorpay_payment_id: String,
    val razorpay_signature: String
)

@Serializable
data class PaymentVerificationResponse(
    val success: Boolean,
    val message: String
)
