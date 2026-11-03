package com.srmfood.gag.domain.usecase.outlet

import com.srmfood.gag.domain.model.OutletOrderingStatus
import com.srmfood.gag.domain.repository.OutletRepository
import javax.inject.Inject

/** Thrown client-side when the outlet is suspended/deactivated; the server enforces the same rule. */
class OutletUnavailableException(val status: OutletOrderingStatus, message: String) : Exception(message)

fun OutletOrderingStatus.studentMessage(outletName: String? = null): String {
    val who = outletName?.takeIf { it.isNotBlank() } ?: "This outlet"
    return when (this) {
        OutletOrderingStatus.SUSPENDED -> "$who has temporarily stopped taking new orders. Orders already placed are not affected. Please try again later or choose another outlet."
        OutletOrderingStatus.DEACTIVATED -> "$who is no longer accepting orders. Please choose another outlet."
        OutletOrderingStatus.UNKNOWN -> "$who can't take orders right now. Please update the app or try again later."
        else -> ""
    }
}

/** Server error text from the order transaction (draft migration) → friendly message. */
fun isOutletSuspendedError(message: String?): Boolean = message?.contains("OUTLET_ORDERING_SUSPENDED") == true

sealed class OutletAvailability {
    /** [verified] is false when the server couldn't be reached (we don't block on that; the server still enforces). */
    data class Available(val verified: Boolean) : OutletAvailability()
    data class Unavailable(val status: OutletOrderingStatus, val outletName: String) : OutletAvailability()
}

/** Always asks the server (never the Room cache) so a stale cache can't hide a suspension. */
class CheckOutletAvailabilityUseCase @Inject constructor(
    private val outletRepository: OutletRepository
) {
    suspend operator fun invoke(outletId: String): OutletAvailability =
        outletRepository.fetchOutlet(outletId).fold(
            onSuccess = { o ->
                if (o.acceptsOrders) OutletAvailability.Available(verified = true)
                else OutletAvailability.Unavailable(
                    // an inactive outlet with an ACTIVE ordering status is still unavailable
                    if (o.orderingStatus.acceptsOrders) OutletOrderingStatus.DEACTIVATED else o.orderingStatus, o.name
                )
            },
            onFailure = { OutletAvailability.Available(verified = false) }
        )
}
