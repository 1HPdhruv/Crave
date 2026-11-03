package com.srmfood.gag.data.repository.supabase

import com.srmfood.gag.data.remote.dto.CreateSupportTicketParams
import com.srmfood.gag.data.remote.dto.SUPPORT_TICKET_COLUMNS
import com.srmfood.gag.data.remote.dto.SupportTicketAssignParams
import com.srmfood.gag.data.remote.dto.SupportTicketBodyParams
import com.srmfood.gag.data.remote.dto.SupportTicketDetailDto
import com.srmfood.gag.data.remote.dto.SupportTicketDto
import com.srmfood.gag.data.remote.dto.SupportTicketIdParams
import com.srmfood.gag.data.remote.dto.SupportTicketMessageDto
import com.srmfood.gag.data.remote.dto.SupportTicketNoteDto
import com.srmfood.gag.data.remote.dto.SupportTicketNoteParams
import com.srmfood.gag.data.remote.dto.SupportTicketPriorityParams
import com.srmfood.gag.data.remote.dto.SupportTicketQueueParams
import com.srmfood.gag.data.remote.dto.SupportTicketQueueRowDto
import com.srmfood.gag.data.remote.dto.SupportTicketStatusParams
import com.srmfood.gag.domain.model.SupportTicket
import com.srmfood.gag.domain.model.SupportTicketCategory
import com.srmfood.gag.domain.model.SupportTicketDetail
import com.srmfood.gag.domain.model.SupportTicketInternalNote
import com.srmfood.gag.domain.model.SupportTicketMessage
import com.srmfood.gag.domain.model.SupportTicketPriority
import com.srmfood.gag.domain.model.SupportTicketQueueFilter
import com.srmfood.gag.domain.model.SupportTicketQueuePage
import com.srmfood.gag.domain.model.SupportTicketStatus
import com.srmfood.gag.domain.repository.SupportTicketRepository
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of [SupportTicketRepository] (migration 023).
 *
 * Reads use PostgREST with an explicit column list (management-only columns are
 * not granted to clients, so `select=*` would be rejected). Every write is an
 * RPC; the database validates the caller, ownership and status transitions.
 */
@Singleton
class SupabaseSupportTicketRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: Auth
) : SupportTicketRepository {

    // ─── Student ──────────────────────────────────────────────────────────────

    override suspend fun createTicket(
        category: SupportTicketCategory,
        subject: String,
        description: String,
        orderId: String?,
        foodItemId: String?,
        outletId: String?,
        clientRequestId: String
    ): Result<SupportTicket> = runCatching {
        requireAuthenticated()
        postgrest.rpc(
            function = "create_support_ticket",
            parameters = CreateSupportTicketParams(
                category = category.name,
                subject = subject.trim(),
                description = description.trim(),
                orderId = orderId,
                foodItemId = foodItemId,
                outletId = outletId,
                clientRequestId = clientRequestId
            )
        ).decodeAs<SupportTicketDto>().toDomain()
    }.onFailure { e -> com.srmfood.gag.core.common.SafeLog.e(TAG, "createTicket failed", e) }

    override suspend fun getMyTickets(): Result<List<SupportTicket>> = runCatching {
        val uid = requireAuthenticated()
        postgrest["support_tickets"].select(Columns.raw(SUPPORT_TICKET_COLUMNS)) {
            filter { eq("student_id", uid) }
            order("created_at", Order.DESCENDING)
        }.decodeList<SupportTicketDto>().map { it.toDomain() }
    }.onFailure { e -> com.srmfood.gag.core.common.SafeLog.e(TAG, "getMyTickets failed", e) }

    override suspend fun getMyTicket(ticketId: String): Result<SupportTicket> = runCatching {
        requireAuthenticated()
        postgrest["support_tickets"].select(Columns.raw(SUPPORT_TICKET_COLUMNS)) {
            filter { eq("id", ticketId) }
        }.decodeSingle<SupportTicketDto>().toDomain()
    }.onFailure { e -> com.srmfood.gag.core.common.SafeLog.e(TAG, "getMyTicket($ticketId) failed", e) }

    override suspend fun getMessages(ticketId: String): Result<List<SupportTicketMessage>> = runCatching {
        requireAuthenticated()
        postgrest["support_ticket_messages"].select(Columns.raw("id,ticket_id,author_role,body,created_at")) {
            filter { eq("ticket_id", ticketId) }
            order("created_at", Order.ASCENDING)
        }.decodeList<SupportTicketMessageDto>().map { it.toDomain() }
    }.onFailure { e -> com.srmfood.gag.core.common.SafeLog.e(TAG, "getMessages($ticketId) failed", e) }

    override suspend fun addStudentMessage(ticketId: String, body: String): Result<SupportTicketMessage> = runCatching {
        requireAuthenticated()
        postgrest.rpc(
            function = "add_support_ticket_message",
            parameters = SupportTicketBodyParams(ticketId = ticketId, body = body.trim())
        ).decodeAs<SupportTicketMessageDto>().toDomain()
    }.onFailure { e -> com.srmfood.gag.core.common.SafeLog.e(TAG, "addStudentMessage($ticketId) failed", e) }

    // ─── Management (ADMIN) ───────────────────────────────────────────────────

    override suspend fun getQueue(
        filter: SupportTicketQueueFilter,
        limit: Int,
        offset: Int
    ): Result<SupportTicketQueuePage> = runCatching {
        requireAuthenticated()
        val rows = postgrest.rpc(
            function = "admin_list_support_tickets",
            parameters = SupportTicketQueueParams(
                statuses = filter.statuses?.map { it.name },
                categories = filter.categories?.map { it.name },
                priorities = filter.priorities?.map { it.name },
                outletId = filter.outletId,
                assignedTo = filter.assignedTo,
                from = filter.fromIso,
                to = filter.toIso,
                search = filter.search?.trim()?.takeIf { it.isNotEmpty() },
                limit = limit,
                offset = offset
            )
        ).decodeList<SupportTicketQueueRowDto>()
        SupportTicketQueuePage(
            tickets = rows.map { it.toDomain() },
            totalCount = rows.firstOrNull()?.totalCount ?: 0
        )
    }.onFailure { e -> com.srmfood.gag.core.common.SafeLog.e(TAG, "getQueue failed", e) }

    override suspend fun getTicketDetail(ticketId: String): Result<SupportTicketDetail> = runCatching {
        requireAuthenticated()
        postgrest.rpc(
            function = "admin_get_support_ticket",
            parameters = SupportTicketIdParams(ticketId)
        ).decodeAs<SupportTicketDetailDto>().toDomain()
    }.onFailure { e -> com.srmfood.gag.core.common.SafeLog.e(TAG, "getTicketDetail($ticketId) failed", e) }

    override suspend fun setStatus(
        ticketId: String,
        status: SupportTicketStatus,
        reason: String?,
        expectedStatus: SupportTicketStatus?
    ): Result<SupportTicket> = runCatching {
        requireAuthenticated()
        postgrest.rpc(
            function = "admin_set_support_ticket_status",
            parameters = SupportTicketStatusParams(
                ticketId = ticketId,
                status = status.name,
                reason = reason?.trim()?.takeIf { it.isNotEmpty() },
                expectedStatus = expectedStatus?.name
            )
        ).decodeAs<SupportTicketDto>().toDomain()
    }.onFailure { e -> com.srmfood.gag.core.common.SafeLog.e(TAG, "setStatus($ticketId) failed", e) }

    override suspend fun setPriority(ticketId: String, priority: SupportTicketPriority): Result<SupportTicket> =
        runCatching {
            requireAuthenticated()
            postgrest.rpc(
                function = "admin_set_support_ticket_priority",
                parameters = SupportTicketPriorityParams(ticketId = ticketId, priority = priority.name)
            ).decodeAs<SupportTicketDto>().toDomain()
        }.onFailure { e -> com.srmfood.gag.core.common.SafeLog.e(TAG, "setPriority($ticketId) failed", e) }

    override suspend fun assign(ticketId: String, assigneeId: String?): Result<SupportTicket> = runCatching {
        requireAuthenticated()
        postgrest.rpc(
            function = "admin_assign_support_ticket",
            parameters = SupportTicketAssignParams(ticketId = ticketId, assignee = assigneeId)
        ).decodeAs<SupportTicketDto>().toDomain()
    }.onFailure { e -> com.srmfood.gag.core.common.SafeLog.e(TAG, "assign($ticketId) failed", e) }

    override suspend fun respond(ticketId: String, body: String): Result<SupportTicketMessage> = runCatching {
        requireAuthenticated()
        postgrest.rpc(
            function = "admin_respond_support_ticket",
            parameters = SupportTicketBodyParams(ticketId = ticketId, body = body.trim())
        ).decodeAs<SupportTicketMessageDto>().toDomain()
    }.onFailure { e -> com.srmfood.gag.core.common.SafeLog.e(TAG, "respond($ticketId) failed", e) }

    override suspend fun addInternalNote(ticketId: String, note: String): Result<SupportTicketInternalNote> =
        runCatching {
            requireAuthenticated()
            postgrest.rpc(
                function = "admin_add_support_ticket_note",
                parameters = SupportTicketNoteParams(ticketId = ticketId, note = note.trim())
            ).decodeAs<SupportTicketNoteDto>().toDomain()
        }.onFailure { e -> com.srmfood.gag.core.common.SafeLog.e(TAG, "addInternalNote($ticketId) failed", e) }

    override suspend fun listAssignees(): Result<List<com.srmfood.gag.domain.model.SupportAssignee>> = runCatching {
        requireAuthenticated()
        postgrest.rpc(function = "admin_list_support_assignees")
            .decodeList<com.srmfood.gag.data.remote.dto.SupportAssigneeDto>().map { it.toDomain() }
    }.onFailure { e -> android.util.Log.w(TAG, "listAssignees unavailable: ${e.message}") }

    override suspend fun getTicketReviews(ticketId: String): Result<com.srmfood.gag.domain.model.SupportTicketReviews> = runCatching {
        requireAuthenticated()
        postgrest.rpc(function = "admin_get_support_ticket_reviews", parameters = SupportTicketIdParams(ticketId))
            .decodeAs<com.srmfood.gag.data.remote.dto.SupportTicketReviewsDto>().toDomain()
    }.onFailure { e -> android.util.Log.w(TAG, "getTicketReviews unavailable: ${e.message}") }

    private fun requireAuthenticated(): String =
        auth.currentSessionOrNull()?.user?.id
            ?: throw IllegalStateException("User must be authenticated to perform this operation.")

    private companion object {
        const val TAG = "SupabaseSupportRepo"
    }
}
