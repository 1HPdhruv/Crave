package com.srmfood.gag.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaymentRecordDto(
    @SerialName("id") val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("razorpay_payment_id") val razorpayPaymentId: String? = null,
    @SerialName("razorpay_order_id") val razorpayOrderId: String? = null,
    @SerialName("amount") val amount: Double,
    @SerialName("currency") val currency: String,
    @SerialName("status") val status: String,
    @SerialName("gateway_provider") val gatewayProvider: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)
