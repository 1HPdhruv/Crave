package com.srmfood.gag.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderDto(
    @SerialName("id") val id: String,
    @SerialName("order_number") val orderNumber: String,
    @SerialName("user_id") val userId: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("outlet_id") val outletId: String,
    @SerialName("outlet_name") val outletName: String = "",
    @SerialName("items") val items: List<OrderItemDto>,
    @SerialName("subtotal") val subtotal: Double,
    @SerialName("tax") val tax: Double,
    @SerialName("total") val total: Double,
    @SerialName("status") val status: String,
    @SerialName("pickup_slot") val pickupSlot: PickupSlotDto? = null,
    @SerialName("estimated_prep_minutes") val estimatedPrepMinutes: Int = 0,
    @SerialName("actual_prep_minutes") val actualPrepMinutes: Int? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("placed_at") val placedAt: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("preparing_at") val preparingAt: String? = null,
    @SerialName("ready_at") val readyAt: String? = null,
    @SerialName("picked_up_at") val pickedUpAt: String? = null,
    @SerialName("cancelled_at") val cancelledAt: String? = null,
    @SerialName("cancellation_reason") val cancellationReason: String? = null,
    @SerialName("payment_status") val paymentStatus: String = "PENDING",
    @SerialName("payment_method") val paymentMethod: String = "PAY_AT_COUNTER",
    @SerialName("special_instructions") val specialInstructions: String? = null,
    @SerialName("qr_token") val qrToken: String? = null
)

@Serializable
data class OrderItemDto(
    @SerialName("id") val id: String,
    @SerialName("food_item_id") val foodItemId: String,
    @SerialName("food_name") val foodName: String,
    @SerialName("food_image_url") val foodImageUrl: String? = null,
    @SerialName("quantity") val quantity: Int,
    @SerialName("unit_price") val unitPrice: Double,
    @SerialName("total_price") val totalPrice: Double,
    @SerialName("order_item_customizations") val customizations: List<OrderItemCustomizationDto> = emptyList(),
    @SerialName("is_veg") val isVeg: Boolean = true
)

@Serializable
data class OrderItemCustomizationDto(
    @SerialName("id") val id: String,
    @SerialName("order_item_id") val orderItemId: String,
    @SerialName("variant_name") val variantName: String,
    @SerialName("option_name") val optionName: String,
    @SerialName("extra_price") val extraPrice: Double
)

@Serializable
data class PickupSlotDto(
    @SerialName("id") val id: String,
    @SerialName("outlet_id") val outletId: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("slot_date") val date: String,
    @SerialName("capacity") val capacity: Int,
    @SerialName("booked_count") val bookedCount: Int,
    @SerialName("status") val status: String
)

@Serializable
data class PlaceOrderRequestDto(
    @SerialName("outlet_id") val outletId: String,
    @SerialName("pickup_slot_id") val pickupSlotId: String,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("special_instructions") val specialInstructions: String? = null
)

@Serializable
data class CancelOrderRequestDto(
    @SerialName("reason") val reason: String
)

@Serializable
data class QrTokenResponseDto(
    @SerialName("token") val token: String,
    @SerialName("expires_at") val expiresAt: String
)

@Serializable
data class ConfirmPickupRequestDto(
    @SerialName("qr_token") val qrToken: String
)

@Serializable
data class RejectOrderRequestDto(
    @SerialName("reason") val reason: String
)
