package com.srmfood.gag.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OutletDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("building") val building: String? = null,
    @SerialName("floor") val floor: String? = null,
    @SerialName("location_description") val locationDescription: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("is_open") val isOpen: Boolean = false,
    @SerialName("open_time") val openTime: String = "08:00",
    @SerialName("close_time") val closeTime: String = "22:00",
    @SerialName("days_open") val daysOpen: List<String> = emptyList(),
    @SerialName("current_queue_size") val currentQueueSize: Int = 0,
    @SerialName("estimated_wait_minutes") val estimatedWaitMinutes: Int = 0,
    @SerialName("categories") val categories: List<String> = emptyList(),
    @SerialName("rating") val rating: Double = 0.0,
    @SerialName("total_reviews") val totalReviews: Int = 0,
    @SerialName("vendor_id") val vendorId: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("phone") val phone: String? = null
)

@Serializable
data class OutletListResponseDto(
    @SerialName("outlets") val outlets: List<OutletDto>,
    @SerialName("total") val total: Int
)
