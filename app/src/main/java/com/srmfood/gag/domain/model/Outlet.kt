package com.srmfood.gag.domain.model

/**
 * Outlet domain model — represents a food outlet/canteen at SRM KTR.
 */
data class Outlet(
    val id: String,
    val name: String,
    val description: String,
    val imageUrl: String?,
    val location: OutletLocation,
    val isOpen: Boolean,
    val operatingHours: OperatingHours,
    val currentQueueSize: Int,
    val estimatedWaitMinutes: Int,
    val categories: List<String>,
    val rating: Double,
    val totalReviews: Int,
    val vendorId: String,
    val isActive: Boolean,
    val phone: String?
)

data class OutletLocation(
    val building: String,
    val floor: String,
    val description: String,
    val latitude: Double?,
    val longitude: Double?
)

data class OperatingHours(
    val openTime: String,   // "08:00"
    val closeTime: String,  // "22:00"
    val daysOpen: List<String>  // ["Mon", "Tue", ...]
)

/** Queue level derived from queue size */
enum class QueueLevel {
    LOW, MODERATE, HIGH, VERY_HIGH;

    companion object {
        fun fromQueueSize(size: Int): QueueLevel = when {
            size <= 5 -> LOW
            size <= 15 -> MODERATE
            size <= 30 -> HIGH
            else -> VERY_HIGH
        }
    }
}
