package com.srmfood.gag.domain.repository

import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.OrderStatus
import com.srmfood.gag.domain.model.PaymentMethod
import com.srmfood.gag.domain.model.PickupSlot
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    fun getOrders(): Flow<List<Order>>
    suspend fun getOrderById(orderId: String): Result<Order>
    suspend fun placeOrder(
        outletId: String,
        pickupSlotId: String,
        paymentMethod: PaymentMethod,
        specialInstructions: String?
    ): Result<Order>
    suspend fun cancelOrder(orderId: String, reason: String): Result<Order>
    suspend fun getPickupSlots(outletId: String, date: String): Result<List<PickupSlot>>
    suspend fun getQrToken(orderId: String): Result<String>
    fun observeOrderStatus(orderId: String): Flow<OrderStatus>

    // Vendor operations
    suspend fun acceptOrder(orderId: String): Result<Order>
    suspend fun rejectOrder(orderId: String, reason: String): Result<Order>
    suspend fun startPreparing(orderId: String): Result<Order>
    suspend fun markReady(orderId: String): Result<Order>
    suspend fun confirmPickup(qrToken: String): Result<Order>
    suspend fun getVendorOrders(status: OrderStatus? = null): Result<List<Order>>
}
