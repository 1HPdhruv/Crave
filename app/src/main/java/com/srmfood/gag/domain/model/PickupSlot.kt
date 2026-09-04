package com.srmfood.gag.domain.model

import kotlinx.serialization.Serializable

/**
 * PickupSlot domain model.
 * Capacity is determined by the backend — client never decides.
 */
@Serializable
data class PickupSlot(
    val id: String,
    val outletId: String,
    val startTime: String,      // "12:30"
    val endTime: String,        // "12:40"
    val date: String,           // "2024-01-15"
    val capacity: Int,
    val bookedCount: Int,
    val status: SlotStatus
) {
    val availableCount: Int get() = capacity - bookedCount
    val displayTime: String get() = "$startTime – $endTime"
    val isSelectable: Boolean get() = status != SlotStatus.FULL
}

@Serializable
enum class SlotStatus {
    AVAILABLE, LIMITED, FULL;

    companion object {
        fun fromString(s: String): SlotStatus = when (s.uppercase()) {
            "FULL" -> FULL
            "LIMITED" -> LIMITED
            else -> AVAILABLE
        }
    }
}



/**
 * Review domain model
 */
data class Review(
    val id: String,
    val userId: String,
    val userName: String,
    val outletId: String?,
    val foodItemId: String?,
    val rating: Int,            // 1–5
    val comment: String?,
    val createdAt: String
)

/**
 * Favorite domain model
 */
data class Favorite(
    val foodItemId: String,
    val foodItem: FoodItem
)
