package com.srmfood.gag.feature.support

import com.srmfood.gag.domain.model.SupportTicketCategory
import com.srmfood.gag.domain.model.SupportTicketPriority
import com.srmfood.gag.domain.model.SupportTicketStatus

fun SupportTicketCategory.label(): String = when (this) {
    SupportTicketCategory.ORDER -> "Order"
    SupportTicketCategory.FOOD_QUALITY -> "Food quality"
    SupportTicketCategory.MISSING_ITEMS -> "Missing items"
    SupportTicketCategory.PAYMENT -> "Payment"
    SupportTicketCategory.PICKUP -> "Pickup"
    SupportTicketCategory.VENDOR_CONDUCT -> "Vendor conduct"
    SupportTicketCategory.OTHER -> "Other"
    SupportTicketCategory.UNKNOWN -> "Unrecognised category"
}

fun SupportTicketStatus.label(): String = when (this) {
    SupportTicketStatus.OPEN -> "Open"
    SupportTicketStatus.UNDER_REVIEW -> "Under review"
    SupportTicketStatus.WAITING_FOR_STUDENT -> "Waiting for you"
    SupportTicketStatus.RESOLVED -> "Resolved"
    SupportTicketStatus.CLOSED -> "Closed"
    SupportTicketStatus.UNKNOWN -> "Unrecognised status"
}

/** Management-side wording ("Waiting for you" only makes sense to the student). */
fun SupportTicketStatus.managementLabel(): String =
    if (this == SupportTicketStatus.WAITING_FOR_STUDENT) "Waiting for student" else label()

fun SupportTicketPriority.label(): String = when (this) {
    SupportTicketPriority.LOW -> "Low"
    SupportTicketPriority.NORMAL -> "Normal"
    SupportTicketPriority.HIGH -> "High"
    SupportTicketPriority.URGENT -> "Urgent"
    SupportTicketPriority.UNKNOWN -> "Unrecognised priority"
}

/**
 * Turns backend/transport failures into text a student or manager can act on.
 * The server's error codes (migration 023) are matched anywhere in the message
 * because PostgREST wraps them ("TICKET_ALREADY_OPEN:<uuid>" etc.).
 */
fun supportErrorMessage(error: Throwable?): String {
    val raw = error?.message.orEmpty()
    return when {
        "TICKET_ALREADY_OPEN" in raw ->
            "You already have an open ticket for this order and issue. Continue the conversation there instead."
        "REQUEST_IN_PROGRESS" in raw ->
            "Your request is still being processed. Please wait a moment and try again."
        "REOPEN_CONFLICT" in raw ->
            "Another active ticket already exists for this order and category. Continue there instead of reopening."
        "TICKET_CHANGED" in raw ->
            "Someone else just updated this ticket. It has been refreshed — please review and try again."
        "INVALID_TRANSITION" in raw -> "That status change isn't allowed from the ticket's current state."
        "REASON_REQUIRED" in raw -> "A reason is required for this change."
        "RATE_LIMITED" in raw -> "You're sending requests too quickly. Please wait a few minutes and try again."
        "TOO_MANY_OPEN_TICKETS" in raw ->
            "You have too many open tickets. Wait for some to be resolved before raising another."
        "TICKET_CLOSED" in raw -> "This ticket is resolved or closed. Raise a new ticket if the problem continues."
        "ORDER_NOT_FOUND" in raw || "OUTLET_MISMATCH" in raw || "FOOD_ITEM_NOT_IN_ORDER" in raw ||
            "FOOD_ITEM_REQUIRES_ORDER" in raw ->
            "We couldn't match this complaint to your order. Go back and try again from the order page."
        "ORDER_REQUIRED" in raw || "OUTLET_REQUIRED" in raw -> "Please link this ticket to the related order."
        "INVALID_SUBJECT" in raw -> "The subject must be 3–120 characters."
        "INVALID_DESCRIPTION" in raw -> "The description must be 10–4000 characters."
        "INVALID_MESSAGE" in raw || "INVALID_NOTE" in raw || "INVALID_REASON" in raw ->
            "That text isn't valid. Please check its length and try again."
        "TICKET_NOT_FOUND" in raw -> "This ticket could not be found or you no longer have access to it."
        "ASSIGNEE_NOT_MANAGEMENT" in raw -> "Tickets can only be assigned to active administrators."
        "Management access required" in raw || "permission denied" in raw.lowercase() ->
            "You don't have permission to do that."
        error is java.io.IOException || "Unable to resolve host" in raw || "timeout" in raw.lowercase() ->
            "Couldn't reach the server. Check your connection and try again."
        else -> "Something went wrong. Please try again."
    }
}

/** Retryable failures keep the same client request id; definitive ones do not need it any more. */
fun isDefinitiveCreateFailure(error: Throwable?): Boolean {
    val raw = error?.message.orEmpty()
    return "TICKET_ALREADY_OPEN" in raw || "INVALID_" in raw || "ORDER_" in raw || "OUTLET_" in raw ||
        "FOOD_ITEM_" in raw || "TOO_MANY_OPEN_TICKETS" in raw
}
