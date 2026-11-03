package com.srmfood.gag.data.remote.dto

import com.srmfood.gag.domain.model.SupportMessageAuthor
import com.srmfood.gag.domain.model.SupportTicket
import com.srmfood.gag.domain.model.SupportTicketCategory
import com.srmfood.gag.domain.model.SupportTicketDetail
import com.srmfood.gag.domain.model.SupportTicketEvent
import com.srmfood.gag.domain.model.SupportTicketInternalNote
import com.srmfood.gag.domain.model.SupportTicketMessage
import com.srmfood.gag.domain.model.SupportTicketPriority
import com.srmfood.gag.domain.model.SupportTicketStatus
import com.srmfood.gag.domain.model.SupportTicketSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// ─── Rows ─────────────────────────────────────────────────────────────────────

/** Student-readable columns of support_tickets. Keep in sync with [SUPPORT_TICKET_COLUMNS]. */
@Serializable
data class SupportTicketDto(
    val id: String,
    @SerialName("ticket_number") val ticketNumber: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("outlet_id") val outletId: String? = null,
    @SerialName("food_item_id") val foodItemId: String? = null,
    val category: String,
    val subject: String,
    val description: String,
    val priority: String,
    val status: String,
    @SerialName("resolution_reason") val resolutionReason: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null,
    // Present only in RPC results (whole-row returns) and the admin detail payload.
    @SerialName("assigned_to") val assignedTo: String? = null
) {
    fun toDomain() = SupportTicket(
        id = id,
        ticketNumber = ticketNumber,
        studentId = studentId,
        orderId = orderId,
        outletId = outletId,
        foodItemId = foodItemId,
        category = SupportTicketCategory.fromString(category),
        subject = subject,
        description = description,
        priority = SupportTicketPriority.fromString(priority),
        status = SupportTicketStatus.fromString(status),
        resolutionReason = resolutionReason,
        createdAt = createdAt,
        updatedAt = updatedAt,
        resolvedAt = resolvedAt,
        closedAt = closedAt
    )
}

/**
 * Explicit column list for student reads. `select=*` would fail: the
 * management-only columns are excluded by a column-level grant.
 */
const val SUPPORT_TICKET_COLUMNS =
    "id,ticket_number,student_id,order_id,outlet_id,food_item_id,category,subject,description," +
        "priority,status,resolution_reason,created_at,updated_at,resolved_at,closed_at"

@Serializable
data class SupportTicketMessageDto(
    val id: String,
    @SerialName("ticket_id") val ticketId: String? = null,
    @SerialName("author_role") val authorRole: String,
    val body: String,
    @SerialName("created_at") val createdAt: String
) {
    fun toDomain(fallbackTicketId: String = "") = SupportTicketMessage(
        id = id,
        ticketId = ticketId ?: fallbackTicketId,
        authorRole = if (authorRole == "MANAGEMENT") SupportMessageAuthor.MANAGEMENT else SupportMessageAuthor.STUDENT,
        body = body,
        createdAt = createdAt
    )
}

@Serializable
data class SupportTicketNoteDto(
    val id: String,
    @SerialName("author_name") val authorName: String? = null,
    val note: String,
    @SerialName("created_at") val createdAt: String
) {
    fun toDomain() = SupportTicketInternalNote(id = id, authorName = authorName, note = note, createdAt = createdAt)
}

@Serializable
data class SupportTicketEventDto(
    val id: Long,
    @SerialName("actor_name") val actorName: String? = null,
    @SerialName("actor_role") val actorRole: String,
    val action: String,
    @SerialName("from_value") val fromValue: String? = null,
    @SerialName("to_value") val toValue: String? = null,
    val metadata: JsonObject? = null,
    @SerialName("created_at") val createdAt: String
) {
    fun toDomain() = SupportTicketEvent(
        id = id,
        actorName = actorName,
        actorRole = actorRole,
        action = action,
        fromValue = fromValue,
        toValue = toValue,
        reason = metadata?.get("reason")?.jsonPrimitive?.contentOrNull,
        createdAt = createdAt
    )
}

@Serializable
data class SupportTicketQueueRowDto(
    val id: String,
    @SerialName("ticket_number") val ticketNumber: String,
    val status: String,
    val priority: String,
    val category: String,
    val subject: String,
    @SerialName("student_name") val studentName: String,
    @SerialName("student_email") val studentEmail: String,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("order_number") val orderNumber: String? = null,
    @SerialName("outlet_id") val outletId: String? = null,
    @SerialName("outlet_name") val outletName: String? = null,
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("assignee_name") val assigneeName: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("total_count") val totalCount: Long = 0
) {
    fun toDomain() = SupportTicketSummary(
        id = id,
        ticketNumber = ticketNumber,
        status = SupportTicketStatus.fromString(status),
        priority = SupportTicketPriority.fromString(priority),
        category = SupportTicketCategory.fromString(category),
        subject = subject,
        studentName = studentName,
        studentEmail = studentEmail,
        orderId = orderId,
        orderNumber = orderNumber,
        outletId = outletId,
        outletName = outletName,
        assignedTo = assignedTo,
        assigneeName = assigneeName,
        createdAt = createdAt,
        updatedAt = updatedAt,
        resolvedAt = resolvedAt
    )
}

// ─── admin_get_support_ticket payload ─────────────────────────────────────────

@Serializable
data class SupportTicketPersonDto(val id: String, val name: String, val email: String? = null)

@Serializable
data class SupportTicketOrderRefDto(
    val id: String,
    @SerialName("order_number") val orderNumber: String,
    val status: String
)

@Serializable
data class SupportTicketOutletRefDto(
    val id: String,
    val name: String,
    @SerialName("is_active") val isActive: Boolean? = null
)

@Serializable
data class SupportTicketDetailDto(
    val ticket: SupportTicketDto,
    val student: SupportTicketPersonDto,
    val order: SupportTicketOrderRefDto? = null,
    val outlet: SupportTicketOutletRefDto? = null,
    val assignee: SupportTicketPersonDto? = null,
    val messages: List<SupportTicketMessageDto> = emptyList(),
    @SerialName("internal_notes") val internalNotes: List<SupportTicketNoteDto> = emptyList(),
    val events: List<SupportTicketEventDto> = emptyList()
) {
    fun toDomain() = SupportTicketDetail(
        ticket = ticket.toDomain(),
        assignedTo = ticket.assignedTo,
        assigneeName = assignee?.name,
        studentName = student.name,
        studentEmail = student.email.orEmpty(),
        orderNumber = order?.orderNumber,
        orderStatus = order?.status,
        outletName = outlet?.name,
        outletIsActive = outlet?.isActive,
        messages = messages.map { it.toDomain(ticket.id) },
        internalNotes = internalNotes.map { it.toDomain() },
        events = events.map { it.toDomain() }
    )
}

// ─── RPC parameters ───────────────────────────────────────────────────────────

@Serializable
data class CreateSupportTicketParams(
    @SerialName("p_category") val category: String,
    @SerialName("p_subject") val subject: String,
    @SerialName("p_description") val description: String,
    @SerialName("p_order_id") val orderId: String? = null,
    @SerialName("p_food_item_id") val foodItemId: String? = null,
    @SerialName("p_outlet_id") val outletId: String? = null,
    @SerialName("p_client_request_id") val clientRequestId: String
)

@Serializable
data class SupportTicketBodyParams(
    @SerialName("p_ticket_id") val ticketId: String,
    @SerialName("p_body") val body: String
)

@Serializable
data class SupportTicketNoteParams(
    @SerialName("p_ticket_id") val ticketId: String,
    @SerialName("p_note") val note: String
)

@Serializable
data class SupportTicketIdParams(@SerialName("p_ticket_id") val ticketId: String)

@Serializable
data class SupportTicketStatusParams(
    @SerialName("p_ticket_id") val ticketId: String,
    @SerialName("p_status") val status: String,
    @SerialName("p_reason") val reason: String? = null,
    @SerialName("p_expected_status") val expectedStatus: String? = null
)

@Serializable
data class SupportTicketPriorityParams(
    @SerialName("p_ticket_id") val ticketId: String,
    @SerialName("p_priority") val priority: String
)

@Serializable
data class SupportTicketAssignParams(
    @SerialName("p_ticket_id") val ticketId: String,
    @SerialName("p_assignee") val assignee: String? = null
)

@Serializable
data class SupportTicketQueueParams(
    @SerialName("p_statuses") val statuses: List<String>? = null,
    @SerialName("p_categories") val categories: List<String>? = null,
    @SerialName("p_priorities") val priorities: List<String>? = null,
    @SerialName("p_outlet_id") val outletId: String? = null,
    @SerialName("p_assigned_to") val assignedTo: String? = null,
    @SerialName("p_from") val from: String? = null,
    @SerialName("p_to") val to: String? = null,
    @SerialName("p_search") val search: String? = null,
    @SerialName("p_limit") val limit: Int = 50,
    @SerialName("p_offset") val offset: Int = 0
)

// ─── Draft helper RPCs ────────────────────────────────────────────────────────

@Serializable
data class SupportAssigneeDto(val id: String, val name: String) {
    fun toDomain() = com.srmfood.gag.domain.model.SupportAssignee(id, name)
}

@Serializable
data class SupportFoodReviewDto(
    val id: String,
    @SerialName("food_name") val foodName: String = "",
    val rating: Int,
    @SerialName("review_text") val reviewText: String? = null,
    @SerialName("is_visible") val isVisible: Boolean = true,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class SupportOutletReviewDto(
    val id: String,
    val rating: Int,
    @SerialName("review_text") val reviewText: String? = null,
    @SerialName("is_visible") val isVisible: Boolean = true,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class SupportTicketReviewsDto(
    @SerialName("food_reviews") val foodReviews: List<SupportFoodReviewDto> = emptyList(),
    @SerialName("outlet_review") val outletReview: SupportOutletReviewDto? = null
) {
    fun toDomain() = com.srmfood.gag.domain.model.SupportTicketReviews(
        foodReviews = foodReviews.map {
            com.srmfood.gag.domain.model.SupportLinkedFoodReview(it.id, it.foodName, it.rating, it.reviewText, it.isVisible, it.createdAt)
        },
        outletReview = outletReview?.let {
            com.srmfood.gag.domain.model.SupportLinkedOutletReview(it.id, it.rating, it.reviewText, it.isVisible, it.createdAt)
        }
    )
}
