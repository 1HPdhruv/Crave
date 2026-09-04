package com.srmfood.gag.domain.usecase.order

import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.OrderStatus
import com.srmfood.gag.domain.model.PaymentMethod
import com.srmfood.gag.domain.model.PickupSlot
import com.srmfood.gag.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetOrdersUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) {
    operator fun invoke(): Flow<List<Order>> = orderRepository.getOrders()
}

class GetOrderDetailsUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) {
    suspend operator fun invoke(orderId: String): Result<Order> =
        orderRepository.getOrderById(orderId)
}

class PlaceOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) {
    suspend operator fun invoke(
        outletId: String,
        pickupSlotId: String,
        paymentMethod: PaymentMethod,
        specialInstructions: String?
    ): Result<Order> {
        if (outletId.isBlank()) return Result.failure(IllegalArgumentException("Outlet required"))
        if (pickupSlotId.isBlank()) return Result.failure(IllegalArgumentException("Pickup slot required"))
        return orderRepository.placeOrder(outletId, pickupSlotId, paymentMethod, specialInstructions)
    }
}

class CancelOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) {
    suspend operator fun invoke(orderId: String, reason: String = "Cancelled by user"): Result<Order> =
        orderRepository.cancelOrder(orderId, reason)
}

class GetPickupSlotsUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) {
    suspend operator fun invoke(outletId: String, date: String): Result<List<PickupSlot>> =
        orderRepository.getPickupSlots(outletId, date)
}

class GetQrTokenUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) {
    suspend operator fun invoke(orderId: String): Result<String> =
        orderRepository.getQrToken(orderId)
}

class ObserveOrderStatusUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) {
    operator fun invoke(orderId: String): Flow<OrderStatus> =
        orderRepository.observeOrderStatus(orderId)
}

// Vendor use cases
class AcceptOrderUseCase @Inject constructor(private val orderRepository: OrderRepository) {
    suspend operator fun invoke(orderId: String) = orderRepository.acceptOrder(orderId)
}

class RejectOrderUseCase @Inject constructor(private val orderRepository: OrderRepository) {
    suspend operator fun invoke(orderId: String, reason: String) = orderRepository.rejectOrder(orderId, reason)
}

class StartPreparingUseCase @Inject constructor(private val orderRepository: OrderRepository) {
    suspend operator fun invoke(orderId: String) = orderRepository.startPreparing(orderId)
}

class MarkReadyUseCase @Inject constructor(private val orderRepository: OrderRepository) {
    suspend operator fun invoke(orderId: String) = orderRepository.markReady(orderId)
}

class ConfirmPickupUseCase @Inject constructor(private val orderRepository: OrderRepository) {
    suspend operator fun invoke(qrToken: String) = orderRepository.confirmPickup(qrToken)
}

class GetVendorOrdersUseCase @Inject constructor(private val orderRepository: OrderRepository) {
    suspend operator fun invoke(status: OrderStatus? = null) = orderRepository.getVendorOrders(status)
}
