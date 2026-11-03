package com.srmfood.gag.domain.model

/**
 * Support ticket (student complaint) — backed by migration 023_support_tickets.sql.
 * Every write goes through a server-side RPC; these models only carry data.
 */
enum class SupportTicketCategory {
    ORDER, FOOD_QUALITY, MISSING_ITEMS, PAYMENT, PICKUP, VENDOR_CONDUCT, OTHER,
    /** Server sent a value this app version does not know; shown visibly, never created by the client. */
    UNKNOWN;

    /** Categories the server rejects without a linked order. */
    val requiresOrder: Boolean
        get() = this == ORDER || this == FOOD_QUALITY || this == MISSING_ITEMS || this == PICKUP

    companion object {
        fun fromString(value: String): SupportTicketCategory =
            entries.firstOrNull { it.name == value.uppercase() } ?: UNKNOWN

        /** Categories a student may choose when raising a ticket. */
        val selectable: List<SupportTicketCategory> get() = entries.filter { it != UNKNOWN }
    }
}

enum class SupportTicketStatus {
    OPEN, UNDER_REVIEW, WAITING_FOR_STUDENT, RESOLVED, CLOSED,
    /** Server sent a status this app version does not know; never treated as OPEN. */
    UNKNOWN;

    val isActive: Boolean get() = this == OPEN || this == UNDER_REVIEW || this == WAITING_FOR_STUDENT

    /** Mirrors the server's transition table; the server remains authoritative. */
    val allowedTransitions: List<SupportTicketStatus>
        get() = when (this) {
            OPEN -> listOf(UNDER_REVIEW, WAITING_FOR_STUDENT, RESOLVED, CLOSED)
            UNDER_REVIEW -> listOf(WAITING_FOR_STUDENT, RESOLVED, CLOSED)
            WAITING_FOR_STUDENT -> listOf(UNDER_REVIEW, RESOLVED, CLOSED)
            RESOLVED -> listOf(CLOSED, UNDER_REVIEW)
            CLOSED, UNKNOWN -> emptyList()
        }

    companion object {
        fun fromString(value: String): SupportTicketStatus =
            entries.firstOrNull { it.name == value.uppercase() } ?: UNKNOWN
    }
}

enum class SupportTicketPriority {
    LOW, NORMAL, HIGH, URGENT, UNKNOWN;

    companion object {
        fun fromString(value: String): SupportTicketPriority =
            entries.firstOrNull { it.name == value.uppercase() } ?: UNKNOWN

        val selectable: List<SupportTicketPriority> get() = entries.filter { it != UNKNOWN }
    }
}

/** Student-visible ticket (management-only fields are never selected). */
data class SupportTicket(
    val id: String,
    val ticketNumber: String,
    val studentId: String,
    val orderId: String?,
    val outletId: String?,
    val foodItemId: String?,
    val category: SupportTicketCategory,
    val subject: String,
    val description: String,
    val priority: SupportTicketPriority,
    val status: SupportTicketStatus,
    val resolutionReason: String?,
    val createdAt: String,
    val updatedAt: String,
    val resolvedAt: String?,
    val closedAt: String?
)

enum class SupportMessageAuthor { STUDENT, MANAGEMENT }

data class SupportTicketMessage(
    val id: String,
    val ticketId: String,
    val authorRole: SupportMessageAuthor,
    val body: String,
    val createdAt: String
)

/** Row in the management queue. */
data class SupportTicketSummary(
    val id: String,
    val ticketNumber: String,
    val status: SupportTicketStatus,
    val priority: SupportTicketPriority,
    val category: SupportTicketCategory,
    val subject: String,
    val studentName: String,
    val studentEmail: String,
    val orderId: String?,
    val orderNumber: String?,
    val outletId: String?,
    val outletName: String?,
    val assignedTo: String?,
    val assigneeName: String?,
    val createdAt: String,
    val updatedAt: String,
    val resolvedAt: String?
)

data class SupportTicketQueuePage(
    val tickets: List<SupportTicketSummary>,
    val totalCount: Long
)

data class SupportTicketInternalNote(
    val id: String,
    val authorName: String?,
    val note: String,
    val createdAt: String
)

data class SupportTicketEvent(
    val id: Long,
    val actorName: String?,
    val actorRole: String,
    val action: String,
    val fromValue: String?,
    val toValue: String?,
    val reason: String?,
    val createdAt: String
)

/** Full management view of one ticket. */
data class SupportTicketDetail(
    val ticket: SupportTicket,
    val assignedTo: String?,
    val assigneeName: String?,
    val studentName: String,
    val studentEmail: String,
    val orderNumber: String?,
    val orderStatus: String?,
    val outletName: String?,
    val outletIsActive: Boolean?,
    val messages: List<SupportTicketMessage>,
    val internalNotes: List<SupportTicketInternalNote>,
    val events: List<SupportTicketEvent>
)

/** Queue filters for management. Null means "no filter". */
data class SupportTicketQueueFilter(
    val statuses: List<SupportTicketStatus>? = null,
    val categories: List<SupportTicketCategory>? = null,
    val priorities: List<SupportTicketPriority>? = null,
    val outletId: String? = null,
    val assignedTo: String? = null,
    val fromIso: String? = null,
    val toIso: String? = null,
    val search: String? = null
)

/** Eligible assignee (active ADMIN) — from admin_list_support_assignees (draft). */
data class SupportAssignee(val id: String, val name: String)

data class SupportLinkedFoodReview(
    val id: String, val foodName: String, val rating: Int, val text: String?, val isVisible: Boolean, val createdAt: String
)

data class SupportLinkedOutletReview(
    val id: String, val rating: Int, val text: String?, val isVisible: Boolean, val createdAt: String
)

/** Reviews the student left for the ticket's order — context for the manager. */
data class SupportTicketReviews(
    val foodReviews: List<SupportLinkedFoodReview>,
    val outletReview: SupportLinkedOutletReview?
)
