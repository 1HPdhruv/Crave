package com.srmfood.gag.domain.model

/** Management actions on an outlet's ordering state (see DRAFT_outlet_suspension.sql). */
enum class OutletOrderingAction {
    FLAG, SUSPEND, DEACTIVATE, REACTIVATE;

    /** Mirrors the draft RPC's transition table; the server remains authoritative. */
    fun isAllowedFrom(status: OutletOrderingStatus): Boolean = when (this) {
        FLAG -> status == OutletOrderingStatus.ACTIVE
        SUSPEND -> status == OutletOrderingStatus.ACTIVE || status == OutletOrderingStatus.FLAGGED
        DEACTIVATE -> status in listOf(OutletOrderingStatus.ACTIVE, OutletOrderingStatus.FLAGGED, OutletOrderingStatus.SUSPENDED)
        REACTIVATE -> status in listOf(OutletOrderingStatus.FLAGGED, OutletOrderingStatus.SUSPENDED, OutletOrderingStatus.DEACTIVATED)
    }

    /** Typing the outlet name back is required for anything that stops (or restarts a closed) outlet. */
    fun requiresNameConfirmation(from: OutletOrderingStatus): Boolean =
        this == SUSPEND || this == DEACTIVATE || (this == REACTIVATE && from == OutletOrderingStatus.DEACTIVATED)

    val minReasonLength: Int get() = if (this == SUSPEND || this == DEACTIVATE) 10 else 3

    companion object {
        fun availableFrom(status: OutletOrderingStatus): List<OutletOrderingAction> =
            entries.filter { it.isAllowedFrom(status) }
    }
}

data class OutletManagementActionRecord(
    val id: Long,
    val action: String,
    val fromStatus: String,
    val toStatus: String,
    val reason: String,
    val createdAt: String
)
