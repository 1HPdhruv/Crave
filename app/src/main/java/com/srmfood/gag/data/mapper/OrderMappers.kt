package com.srmfood.gag.data.mapper

import com.srmfood.gag.data.local.entity.OrderEntity
import com.srmfood.gag.data.remote.dto.OrderDto
import com.srmfood.gag.data.remote.dto.OrderItemDto
import com.srmfood.gag.data.remote.dto.PickupSlotDto
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.OrderItem
import com.srmfood.gag.domain.model.OrderStatus
import com.srmfood.gag.domain.model.PaymentMethod
import com.srmfood.gag.domain.model.PaymentStatus
import com.srmfood.gag.domain.model.PickupSlot
import com.srmfood.gag.domain.model.SlotStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun OrderDto.toDomain(): Order = Order(
    id = id,
    orderNumber = orderNumber,
    userId = userId,
    vendorId = vendorId,
    outletId = outletId,
    outletName = outletName,
    items = items.map { it.toDomain() },
    subtotal = subtotal,
    tax = tax,
    total = total,
    status = OrderStatus.valueOf(status),
    pickupSlot = pickupSlot?.toDomain(),
    estimatedPrepMinutes = estimatedPrepMinutes,
    actualPrepMinutes = actualPrepMinutes,
    createdAt = createdAt,
    placedAt = placedAt,
    acceptedAt = acceptedAt,
    preparingAt = preparingAt,
    readyAt = readyAt,
    pickedUpAt = pickedUpAt,
    cancelledAt = cancelledAt,
    cancellationReason = cancellationReason,
    paymentStatus = PaymentStatus.fromString(paymentStatus),
    paymentMethod = PaymentMethod.fromString(paymentMethod),
    specialInstructions = specialInstructions,
    qrToken = qrToken
)

fun OrderDto.toEntity(): OrderEntity = OrderEntity(
    id = id,
    orderNumber = orderNumber,
    userId = userId,
    vendorId = vendorId,
    outletId = outletId,
    outletName = outletName,
    items = Json.encodeToString(items),
    subtotal = subtotal,
    tax = tax,
    total = total,
    status = status,
    pickupSlot = pickupSlot?.let { Json.encodeToString(it) },
    estimatedPrepMinutes = estimatedPrepMinutes,
    actualPrepMinutes = actualPrepMinutes,
    createdAt = createdAt,
    placedAt = placedAt,
    acceptedAt = acceptedAt,
    preparingAt = preparingAt,
    readyAt = readyAt,
    pickedUpAt = pickedUpAt,
    cancelledAt = cancelledAt,
    cancellationReason = cancellationReason,
    paymentStatus = paymentStatus,
    paymentMethod = paymentMethod,
    specialInstructions = specialInstructions,
    qrToken = qrToken
)

fun OrderEntity.toDomain(): Order = Order(
    id = id,
    orderNumber = orderNumber,
    userId = userId,
    vendorId = vendorId,
    outletId = outletId,
    outletName = outletName,
    items = try { Json.decodeFromString<List<OrderItemDto>>(items).map { it.toDomain() } } catch (e: Exception) { emptyList() },
    subtotal = subtotal,
    tax = tax,
    total = total,
    status = OrderStatus.valueOf(status),
    pickupSlot = pickupSlot?.let { try { Json.decodeFromString<PickupSlotDto>(it).toDomain() } catch (e: Exception) { null } },
    estimatedPrepMinutes = estimatedPrepMinutes,
    actualPrepMinutes = actualPrepMinutes,
    createdAt = createdAt,
    placedAt = placedAt,
    acceptedAt = acceptedAt,
    preparingAt = preparingAt,
    readyAt = readyAt,
    pickedUpAt = pickedUpAt,
    cancelledAt = cancelledAt,
    cancellationReason = cancellationReason,
    paymentStatus = PaymentStatus.fromString(paymentStatus),
    paymentMethod = PaymentMethod.fromString(paymentMethod),
    specialInstructions = specialInstructions,
    qrToken = qrToken
)

fun OrderItemDto.toDomain(): OrderItem = OrderItem(
    id = id,
    foodItemId = foodItemId,
    foodName = foodName,
    foodImageUrl = foodImageUrl,
    quantity = quantity,
    unitPrice = unitPrice,
    totalPrice = totalPrice,
    customizations = customizations.map { "${it.variantName}: ${it.optionName}" },
    isVeg = isVeg
)

fun PickupSlotDto.toDomain(): PickupSlot = PickupSlot(
    id = id,
    outletId = outletId,
    startTime = startTime,
    endTime = endTime,
    date = date,
    capacity = capacity,
    bookedCount = bookedCount,
    status = SlotStatus.fromString(status)
)
