package com.srmfood.gag.data.mapper

import com.srmfood.gag.data.local.entity.OutletEntity
import com.srmfood.gag.data.remote.dto.OutletDto
import com.srmfood.gag.domain.model.OutletLocation
import com.srmfood.gag.domain.model.OperatingHours
import com.srmfood.gag.domain.model.Outlet
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun OutletDto.toDomain(): Outlet = Outlet(
    id = id,
    name = name,
    description = description ?: "",
    imageUrl = imageUrl,
    location = OutletLocation(
        building = building ?: "",
        floor = floor ?: "",
        description = locationDescription ?: "",
        latitude = latitude,
        longitude = longitude
    ),
    isOpen = isOpen,
    operatingHours = OperatingHours(
        openTime = openTime,
        closeTime = closeTime,
        daysOpen = daysOpen
    ),
    currentQueueSize = currentQueueSize,
    estimatedWaitMinutes = estimatedWaitMinutes,
    categories = categories,
    rating = rating,
    totalReviews = totalReviews,
    vendorId = vendorId ?: "",
    isActive = isActive,
    phone = phone
)

fun OutletDto.toEntity(): OutletEntity = OutletEntity(
    id = id,
    name = name,
    description = description ?: "",
    imageUrl = imageUrl,
    building = building ?: "",
    floor = floor ?: "",
    locationDescription = locationDescription ?: "",
    latitude = latitude,
    longitude = longitude,
    isOpen = isOpen,
    openTime = openTime,
    closeTime = closeTime,
    daysOpen = Json.encodeToString(daysOpen),
    currentQueueSize = currentQueueSize,
    estimatedWaitMinutes = estimatedWaitMinutes,
    categories = Json.encodeToString(categories),
    rating = rating,
    totalReviews = totalReviews,
    vendorId = vendorId ?: "",
    isActive = isActive,
    phone = phone
)

fun OutletEntity.toDomain(): Outlet = Outlet(
    id = id,
    name = name,
    description = description,
    imageUrl = imageUrl,
    location = OutletLocation(
        building = building,
        floor = floor,
        description = locationDescription,
        latitude = latitude,
        longitude = longitude
    ),
    isOpen = isOpen,
    operatingHours = OperatingHours(
        openTime = openTime,
        closeTime = closeTime,
        daysOpen = Json.decodeFromString<List<String>>(daysOpen)
    ),
    currentQueueSize = currentQueueSize,
    estimatedWaitMinutes = estimatedWaitMinutes,
    categories = Json.decodeFromString<List<String>>(categories),
    rating = rating,
    totalReviews = totalReviews,
    vendorId = vendorId,
    isActive = isActive,
    phone = phone
)
