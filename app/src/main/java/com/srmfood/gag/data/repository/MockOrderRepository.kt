package com.srmfood.gag.data.repository

import com.srmfood.gag.data.local.dao.OrderDao
import com.srmfood.gag.data.mapper.toDomain
import com.srmfood.gag.data.mapper.toEntity
import com.srmfood.gag.data.remote.api.OrderApi
import com.srmfood.gag.data.remote.api.VendorApi
import com.srmfood.gag.data.remote.dto.CancelOrderRequestDto
import com.srmfood.gag.data.remote.dto.ConfirmPickupRequestDto
import com.srmfood.gag.data.remote.dto.PlaceOrderRequestDto
import com.srmfood.gag.data.remote.dto.RejectOrderRequestDto
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.OrderStatus
import com.srmfood.gag.domain.model.PaymentMethod
import com.srmfood.gag.domain.model.PickupSlot
import com.srmfood.gag.domain.repository.OrderRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockOrderRepository @Inject constructor(
    private val orderApi: OrderApi,
    private val vendorApi: VendorApi,
    private val orderDao: OrderDao
) : OrderRepository {

    override fun getOrders(): Flow<List<Order>> {
        return orderDao.observeOrders().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getOrderById(orderId: String): Result<Order> = runCatching {
        val response = orderApi.getOrderById(orderId)
        orderDao.insertOrder(response.toEntity())
        response.toDomain()
    }

    override suspend fun placeOrder(
        outletId: String,
        pickupSlotId: String,
        paymentMethod: PaymentMethod,
        specialInstructions: String?
    ): Result<Order> = runCatching {
        val request = PlaceOrderRequestDto(
            outletId = outletId,
            pickupSlotId = pickupSlotId,
            paymentMethod = paymentMethod.name,
            specialInstructions = specialInstructions
        )
        val response = orderApi.placeOrder(request)
        orderDao.insertOrder(response.toEntity())
        response.toDomain()
    }

    override suspend fun cancelOrder(orderId: String, reason: String): Result<Order> = runCatching {
        val response = orderApi.cancelOrder(orderId, CancelOrderRequestDto(reason))
        orderDao.updateStatus(orderId, response.status)
        response.toDomain()
    }

    override suspend fun getPickupSlots(outletId: String, date: String): Result<List<PickupSlot>> = runCatching {
        val response = orderApi.getPickupSlots(outletId, date)
        response.map { it.toDomain() }
    }

    override suspend fun getQrToken(orderId: String): Result<String> = runCatching {
        val response = orderApi.getQrToken(orderId)
        response.token
    }

    override fun observeOrderStatus(orderId: String): Flow<OrderStatus> = flow {
        while (true) {
            val local = orderDao.getOrderById(orderId)
            local?.let { emit(OrderStatus.valueOf(it.status)) }
            
            // Poll API for updates (simplified for now)
            val remote = orderApi.getOrderById(orderId)
            if (local?.status != remote.status) {
                orderDao.updateStatus(orderId, remote.status)
            }
            emit(OrderStatus.valueOf(remote.status))
            delay(5000)
        }
    }

    // ─── Vendor Operations ────────────────────────────────────────────────────────
    
    override suspend fun getVendorOrders(status: OrderStatus?): Result<List<Order>> = runCatching {
        val response = vendorApi.getVendorOrders(status?.name)
        val entities = response.map { it.toEntity() }
        orderDao.insertAll(entities)
        response.map { it.toDomain() }
    }

    override suspend fun acceptOrder(orderId: String): Result<Order> = runCatching {
        val response = vendorApi.acceptOrder(orderId)
        orderDao.insertOrder(response.toEntity())
        response.toDomain()
    }

    override suspend fun rejectOrder(orderId: String, reason: String): Result<Order> = runCatching {
        val response = vendorApi.rejectOrder(orderId, RejectOrderRequestDto(reason))
        orderDao.insertOrder(response.toEntity())
        response.toDomain()
    }

    override suspend fun startPreparing(orderId: String): Result<Order> = runCatching {
        val response = vendorApi.startPreparing(orderId)
        orderDao.insertOrder(response.toEntity())
        response.toDomain()
    }

    override suspend fun markReady(orderId: String): Result<Order> = runCatching {
        val response = vendorApi.markReady(orderId)
        orderDao.insertOrder(response.toEntity())
        response.toDomain()
    }

    override suspend fun confirmPickup(qrToken: String): Result<Order> = runCatching {
        val response = vendorApi.confirmPickup(ConfirmPickupRequestDto(qrToken))
        orderDao.insertOrder(response.toEntity())
        response.toDomain()
    }
}
