package com.srmfood.gag.domain.repository

import com.srmfood.gag.domain.model.SupportTicket
import com.srmfood.gag.domain.model.SupportTicketCategory
import com.srmfood.gag.domain.model.SupportTicketDetail
import com.srmfood.gag.domain.model.SupportTicketInternalNote
import com.srmfood.gag.domain.model.SupportTicketMessage
import com.srmfood.gag.domain.model.SupportTicketPriority
import com.srmfood.gag.domain.model.SupportTicketQueueFilter
import com.srmfood.gag.domain.model.SupportTicketQueuePage
import com.srmfood.gag.domain.model.SupportTicketStatus

/**
 * Support tickets (migration 023_support_tickets.sql).
 *
 * Authorization lives entirely in the database: student calls are limited to
 * the caller's own tickets by RLS + RPC checks, and every management call is
 * rejected server-side unless the caller is an active ADMIN.
 */
interface SupportTicketRepository {

    // ─── Student ──────────────────────────────────────────────────────────────

    /**
     * Raises a ticket. The server derives the outlet from [orderId] and checks
     * that the order (and [foodItemId], if given) belong to the caller.
     * Reusing [clientRequestId] on retry returns the original ticket.
     */
    suspend fun createTicket(
        category: SupportTicketCategory,
        subject: String,
        description: String,
        orderId: String?,
        foodItemId: String?,
        outletId: String?,
        clientRequestId: String
    ): Result<SupportTicket>

    suspend fun getMyTickets(): Result<List<SupportTicket>>

    suspend fun getMyTicket(ticketId: String): Result<SupportTicket>

    suspend fun getMessages(ticketId: String): Result<List<SupportTicketMessage>>

    suspend fun addStudentMessage(ticketId: String, body: String): Result<SupportTicketMessage>

    // ─── Management (ADMIN) ───────────────────────────────────────────────────

    suspend fun getQueue(
        filter: SupportTicketQueueFilter,
        limit: Int = 50,
        offset: Int = 0
    ): Result<SupportTicketQueuePage>

    suspend fun getTicketDetail(ticketId: String): Result<SupportTicketDetail>

    /** [expectedStatus] makes the change fail if another manager already moved the ticket. */
    suspend fun setStatus(
        ticketId: String,
        status: SupportTicketStatus,
        reason: String?,
        expectedStatus: SupportTicketStatus?
    ): Result<SupportTicket>

    suspend fun setPriority(ticketId: String, priority: SupportTicketPriority): Result<SupportTicket>

    /** Pass null to unassign. */
    suspend fun assign(ticketId: String, assigneeId: String?): Result<SupportTicket>

    /** Student-facing response. */
    suspend fun respond(ticketId: String, body: String): Result<SupportTicketMessage>

    /** Eligible assignees (active admins). Needs DRAFT_support_admin_helpers.sql on the server. */
    suspend fun listAssignees(): Result<List<com.srmfood.gag.domain.model.SupportAssignee>>

    /** Reviews linked to the ticket's order. Needs DRAFT_support_admin_helpers.sql on the server. */
    suspend fun getTicketReviews(ticketId: String): Result<com.srmfood.gag.domain.model.SupportTicketReviews>

    /** Management-only note; never visible to students or vendors. */
    suspend fun addInternalNote(ticketId: String, note: String): Result<SupportTicketInternalNote>
}
