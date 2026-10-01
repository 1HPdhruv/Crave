package com.srmfood.gag.domain.model

data class PaymentRecord(
    val id: String,
    val orderId: String,
    val razorpayPaymentId: String?,
    val razorpayOrderId: String?,
    val amount: Double,
    val currency: String,
    val status: String,
    val gatewayProvider: String,
    val createdAt: String,
    val updatedAt: String
)
