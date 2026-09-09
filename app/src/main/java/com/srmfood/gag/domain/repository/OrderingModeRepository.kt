package com.srmfood.gag.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Ordering mode domain contract.
 *
 * Single source of truth for whether the student is ordering for
 * hostel delivery or restaurant pickup.
 *
 * Persisted in DataStore so the mode survives navigation across
 * Home → Restaurant → Cart → Checkout without requiring explicit
 * prop-drilling through the nav graph.
 */

enum class OrderingMode {
    PICKUP, DELIVERY
}

/**
 * Hostel delivery address.
 * All fields are strings to match student-facing text input.
 * Required for DELIVERY orders: hostel, block, room.
 */
data class HostelAddress(
    val hostel: String = "",    // e.g. "A Block Hostel"
    val block: String = "",     // e.g. "B Block"
    val room: String = "",      // e.g. "204"
    val notes: String = ""      // optional delivery notes
) {
    val isComplete: Boolean
        get() = hostel.isNotBlank() && block.isNotBlank() && room.isNotBlank()

    /** Human-readable one-liner used in UI summaries. */
    val displaySummary: String
        get() = if (isComplete) "$hostel • $block • Room $room" else ""

    /** Used as specialInstructions when placing a delivery order. */
    fun toDeliveryInstructions(): String {
        val base = "DELIVERY: $hostel, $block, Room $room"
        return if (notes.isNotBlank()) "$base. Note: $notes" else base
    }
}

interface OrderingModeRepository {
    val orderingMode: Flow<OrderingMode>
    val hostelAddress: Flow<HostelAddress>

    suspend fun setOrderingMode(mode: OrderingMode)
    suspend fun setHostelAddress(address: HostelAddress)
}
