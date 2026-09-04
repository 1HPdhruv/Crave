package com.srmfood.gag.domain.model

/**
 * Order domain model — complete order lifecycle.
 *
 * Server is ALWAYS authoritative for order status.
 * Never update status client-side; always fetch from API.
 */
data class Order(
    val id: String,
    val orderNumber: String,        // Human-readable, e.g. #A482
    val userId: String,
    val vendorId: String,
    val outletId: String,
    val outletName: String,
    val items: List<OrderItem>,
    val subtotal: Double,
    val tax: Double,
    val total: Double,
    val status: OrderStatus,
    val pickupSlot: PickupSlot?,
    val estimatedPrepMinutes: Int,
    val actualPrepMinutes: Int?,
    val createdAt: String,
    val placedAt: String?,
    val acceptedAt: String?,
    val preparingAt: String?,
    val readyAt: String?,
    val pickedUpAt: String?,
    val cancelledAt: String?,
    val cancellationReason: String?,
    val paymentStatus: PaymentStatus,
    val paymentMethod: PaymentMethod,
    val specialInstructions: String?,
    val qrToken: String?            // Short-lived pickup token — not sensitive by itself
)

data class OrderItem(
    val id: String,
    val foodItemId: String,
    val foodName: String,
    val foodImageUrl: String?,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val customizations: List<String>,
    val isVeg: Boolean
)

enum class OrderStatus {
    CREATED, PLACED, ACCEPTED, PREPARING, READY, PICKED_UP,
    REJECTED, CANCELLED, EXPIRED, REFUNDED;

    companion object {
        fun fromString(status: String): OrderStatus = when (status.uppercase()) {
            "CREATED" -> CREATED
            "PLACED" -> PLACED
            "ACCEPTED" -> ACCEPTED
            "PREPARING" -> PREPARING
            "READY" -> READY
            "PICKED_UP" -> PICKED_UP
            "REJECTED" -> REJECTED
            "CANCELLED" -> CANCELLED
            "EXPIRED" -> EXPIRED
            "REFUNDED" -> REFUNDED
            else -> CREATED
        }
    }

    val isTerminal: Boolean get() = this in listOf(PICKED_UP, REJECTED, CANCELLED, EXPIRED, REFUNDED)
    val isActive: Boolean get() = this in listOf(PLACED, ACCEPTED, PREPARING, READY)
    val displayName: String get() = when (this) {
        CREATED -> "Created"
        PLACED -> "Order Placed"
        ACCEPTED -> "Accepted"
        PREPARING -> "Preparing"
        READY -> "Ready for Pickup"
        PICKED_UP -> "Picked Up"
        REJECTED -> "Rejected"
        CANCELLED -> "Cancelled"
        EXPIRED -> "Expired"
        REFUNDED -> "Refunded"
    }
}

enum class PaymentStatus {
    PENDING, PAID, FAILED, REFUNDED;

    companion object {
        fun fromString(s: String): PaymentStatus = valueOf(s.uppercase())
    }
}

enum class PaymentMethod {
    PAY_AT_COUNTER, ONLINE;

    companion object {
        fun fromString(s: String): PaymentMethod = when (s.uppercase()) {
            "ONLINE" -> ONLINE
            else -> PAY_AT_COUNTER
        }
    }

    val displayName: String get() = when (this) {
        PAY_AT_COUNTER -> "Pay at Counter"
        ONLINE -> "Online Payment"
    }
}
