package com.srmfood.gag.feature.support

import com.srmfood.gag.data.remote.dto.SupportTicketDto
import com.srmfood.gag.domain.model.SupportTicketCategory
import com.srmfood.gag.domain.model.SupportTicketPriority
import com.srmfood.gag.domain.model.SupportTicketStatus
import org.junit.Assert.*
import org.junit.Test

class SupportLogicTest {

    @Test fun `unknown server values never become OPEN or a valid category`() {
        assertEquals(SupportTicketStatus.UNKNOWN, SupportTicketStatus.fromString("ESCALATED"))
        assertEquals(SupportTicketCategory.UNKNOWN, SupportTicketCategory.fromString("SOMETHING_NEW"))
        assertEquals(SupportTicketPriority.UNKNOWN, SupportTicketPriority.fromString("CRITICAL"))
        assertFalse(SupportTicketStatus.UNKNOWN.isActive)
        assertTrue(SupportTicketStatus.UNKNOWN.allowedTransitions.isEmpty())
        assertFalse(SupportTicketCategory.selectable.contains(SupportTicketCategory.UNKNOWN))
    }

    @Test fun `known values parse case-insensitively`() {
        assertEquals(SupportTicketStatus.WAITING_FOR_STUDENT, SupportTicketStatus.fromString("waiting_for_student"))
    }

    @Test fun `dto maps unknown status safely`() {
        val dto = SupportTicketDto(
            id = "1", ticketNumber = "TKT-1", studentId = "s", category = "OTHER", subject = "x", description = "y",
            priority = "NORMAL", status = "WEIRD", createdAt = "t", updatedAt = "t"
        )
        assertEquals(SupportTicketStatus.UNKNOWN, dto.toDomain().status)
    }

    @Test fun `transition table mirrors the server`() {
        assertEquals(listOf(SupportTicketStatus.CLOSED, SupportTicketStatus.UNDER_REVIEW), SupportTicketStatus.RESOLVED.allowedTransitions)
        assertTrue(SupportTicketStatus.CLOSED.allowedTransitions.isEmpty())
        assertFalse(SupportTicketStatus.OPEN.allowedTransitions.contains(SupportTicketStatus.OPEN))
    }

    @Test fun `reason required for resolve close and reopen only`() {
        val u = SupportTicketStatus.UNDER_REVIEW
        assertTrue(u.requiresReasonWhenMovingTo(SupportTicketStatus.RESOLVED))
        assertTrue(u.requiresReasonWhenMovingTo(SupportTicketStatus.CLOSED))
        assertFalse(u.requiresReasonWhenMovingTo(SupportTicketStatus.WAITING_FOR_STUDENT))
        assertTrue(SupportTicketStatus.RESOLVED.requiresReasonWhenMovingTo(SupportTicketStatus.UNDER_REVIEW))
    }

    @Test fun `backend error codes become friendly messages`() {
        assertTrue(supportErrorMessage(Exception("TICKET_ALREADY_OPEN:abc")).contains("already have an open ticket"))
        assertTrue(supportErrorMessage(Exception("REQUEST_IN_PROGRESS: retry the same request.")).contains("still being processed"))
        assertTrue(supportErrorMessage(Exception("REOPEN_CONFLICT:")).contains("Another active ticket"))
        assertTrue(supportErrorMessage(Exception("TICKET_CHANGED:RESOLVED")).contains("updated"))
        assertTrue(supportErrorMessage(java.io.IOException("Unable to resolve host")).contains("connection"))
        assertEquals("Something went wrong. Please try again.", supportErrorMessage(Exception("boom")))
    }

    @Test fun `only definitive failures drop the request id`() {
        assertTrue(isDefinitiveCreateFailure(Exception("TICKET_ALREADY_OPEN:x")))
        assertFalse(isDefinitiveCreateFailure(Exception("REQUEST_IN_PROGRESS")))
        assertFalse(isDefinitiveCreateFailure(java.io.IOException("timeout")))
        assertFalse(isDefinitiveCreateFailure(Exception("RATE_LIMITED")))
    }

    @Test fun `form validation bounds`() {
        assertTrue(validateTicketForm("ab", "short").hasErrors)
        assertFalse(validateTicketForm("Cold food", "The food was cold on pickup").hasErrors)
        assertNotNull(validateReason("x"))
        assertNull(validateReason("Refund issued"))
    }
}
