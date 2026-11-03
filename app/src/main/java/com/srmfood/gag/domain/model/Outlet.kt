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
    val phone: String?,
    /** Management-controlled ordering state; independent of [isOpen] (hours) and [isActive]. */
    val orderingStatus: OutletOrderingStatus = OutletOrderingStatus.ACTIVE
) {
    /** True when students may add items / check out. Opening hours are checked separately. */
    val acceptsOrders: Boolean get() = isActive && orderingStatus.acceptsOrders
}

enum class OutletOrderingStatus {
    ACTIVE, FLAGGED, SUSPENDED, DEACTIVATED,
    /** Unrecognised server value: treated as unavailable (fail safe), never as ACTIVE. */
    UNKNOWN;

    /** FLAGGED is an internal monitoring marker; students can still order. */
    val acceptsOrders: Boolean get() = this == ACTIVE || this == FLAGGED

    companion object {
        /** A missing value (column not deployed yet) means ACTIVE; an unknown string does not. */
        fun fromString(value: String?): OutletOrderingStatus = when {
            value == null -> ACTIVE
            else -> entries.firstOrNull { it.name == value.uppercase() } ?: UNKNOWN
        }
    }
}

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
