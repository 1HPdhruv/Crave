package com.srmfood.gag.feature.vendor.catalog

import com.srmfood.gag.domain.model.vendor.ConflictException

/** Turns repository / PostgREST failures into text a vendor can act on. */
fun vendorErrorMessage(e: Throwable?): String {
    val raw = e?.message.orEmpty()
    return when {
        e is ConflictException -> raw
        e is IllegalArgumentException || e is IllegalStateException && raw.startsWith("Please") -> raw.ifBlank { "Please check the highlighted fields." }
        e is SecurityException -> raw
        "price greater than 0" in raw -> "Price must be greater than 0."
        "prep_time" in raw -> "Preparation time can't be negative."
        "only manage food items for their own outlet" in raw -> "You can only manage items of your own outlet."
        "duplicate key" in raw || "23505" in raw || "already exists" in raw -> "A slot starting at that time already exists."
        "foreign key" in raw || "23503" in raw -> "This can't be removed because orders refer to it."
        "check constraint" in raw || "23514" in raw -> "That value isn't allowed (for example, capacity below the number of bookings)."
        "row-level security" in raw || "permission denied" in raw.lowercase() -> "You don't have permission to do that."
        e is java.io.IOException || "Unable to resolve host" in raw || "timeout" in raw.lowercase() -> "Couldn't reach the server. Check your connection and try again."
        else -> "Something went wrong. Please try again."
    }
}
