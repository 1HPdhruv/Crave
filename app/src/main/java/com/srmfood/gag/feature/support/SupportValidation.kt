package com.srmfood.gag.feature.support

/** Client-side hints only; the server re-validates everything (migration 023). */
object SupportLimits {
    const val SUBJECT_MIN = 3
    const val SUBJECT_MAX = 120
    const val DESCRIPTION_MIN = 10
    const val DESCRIPTION_MAX = 4000
    const val MESSAGE_MAX = 4000
    const val NOTE_MAX = 4000
    const val REASON_MIN = 3
    const val REASON_MAX = 2000
}

data class TicketFormErrors(val subject: String? = null, val description: String? = null) {
    val hasErrors get() = subject != null || description != null
}

fun validateTicketForm(subject: String, description: String): TicketFormErrors {
    val s = subject.trim()
    val d = description.trim()
    return TicketFormErrors(
        subject = when {
            s.length < SupportLimits.SUBJECT_MIN -> "Enter at least ${SupportLimits.SUBJECT_MIN} characters"
            s.length > SupportLimits.SUBJECT_MAX -> "Keep it under ${SupportLimits.SUBJECT_MAX} characters"
            else -> null
        },
        description = when {
            d.length < SupportLimits.DESCRIPTION_MIN -> "Describe the problem in at least ${SupportLimits.DESCRIPTION_MIN} characters"
            d.length > SupportLimits.DESCRIPTION_MAX -> "Keep it under ${SupportLimits.DESCRIPTION_MAX} characters"
            else -> null
        }
    )
}

fun validateReason(reason: String): String? {
    val r = reason.trim()
    return when {
        r.length < SupportLimits.REASON_MIN -> "Enter at least ${SupportLimits.REASON_MIN} characters"
        r.length > SupportLimits.REASON_MAX -> "Keep it under ${SupportLimits.REASON_MAX} characters"
        else -> null
    }
}

/** Management statuses that need a written reason (RESOLVED/CLOSED, or reopening a resolved ticket). */
fun com.srmfood.gag.domain.model.SupportTicketStatus.requiresReasonWhenMovingTo(
    target: com.srmfood.gag.domain.model.SupportTicketStatus
): Boolean = target == com.srmfood.gag.domain.model.SupportTicketStatus.RESOLVED ||
    target == com.srmfood.gag.domain.model.SupportTicketStatus.CLOSED ||
    (this == com.srmfood.gag.domain.model.SupportTicketStatus.RESOLVED &&
        target == com.srmfood.gag.domain.model.SupportTicketStatus.UNDER_REVIEW)
