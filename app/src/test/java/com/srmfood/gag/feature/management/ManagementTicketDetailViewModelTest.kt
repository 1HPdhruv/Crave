package com.srmfood.gag.feature.management

import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.*
import com.srmfood.gag.domain.repository.SupportTicketRepository
import io.github.jan.supabase.gotrue.Auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ManagementTicketDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo: SupportTicketRepository = mock()
    private val auth: Auth = mock()
    private var assignees: Result<List<SupportAssignee>> = Result.failure(Exception("PGRST202"))
    private var reviews: Result<SupportTicketReviews> = Result.failure(Exception("PGRST202"))

    private fun ticket(status: SupportTicketStatus) = SupportTicket(
        "t1", "TKT-1", "s", "o1", "ou1", null, SupportTicketCategory.ORDER, "subj", "desc",
        SupportTicketPriority.NORMAL, status, null, "c", "u", null, null
    )
    private fun detail(status: SupportTicketStatus) = SupportTicketDetail(
        ticket(status), null, null, "Student", "s@x.in", "A1", "READY", "Outlet", true, emptyList(), emptyList(), emptyList()
    )

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private suspend fun vm(status: SupportTicketStatus): ManagementTicketDetailViewModel {
        whenever(repo.getTicketDetail("t1")).thenReturn(Result.success(detail(status)))
        whenever(repo.listAssignees()).thenReturn(assignees)
        whenever(repo.getTicketReviews("t1")).thenReturn(reviews)
        return ManagementTicketDetailViewModel(repo, auth).also { it.load("t1"); dispatcher.scheduler.advanceUntilIdle() }
    }

    @Test fun `resolving needs a reason and passes the expected status`() = runTest(dispatcher) {
        val vm = vm(SupportTicketStatus.UNDER_REVIEW)
        whenever(repo.setStatus(any(), any(), anyOrNull(), anyOrNull())).thenReturn(Result.success(ticket(SupportTicketStatus.RESOLVED)))
        vm.requestTransition(SupportTicketStatus.RESOLVED)
        assertEquals(SupportTicketStatus.RESOLVED, vm.state.value.pendingTransition)
        vm.confirmTransition()
        assertNotNull(vm.state.value.reasonError)
        verify(repo, times(0)).setStatus(any(), any(), anyOrNull(), anyOrNull())
        vm.setReasonDraft("Refund issued at counter")
        vm.confirmTransition(); advanceUntilIdle()
        verify(repo).setStatus(eq("t1"), eq(SupportTicketStatus.RESOLVED), eq("Refund issued at counter"), eq(SupportTicketStatus.UNDER_REVIEW))
        assertNull(vm.state.value.pendingTransition)
    }

    @Test fun `reopening a resolved ticket needs a reason`() = runTest(dispatcher) {
        val vm = vm(SupportTicketStatus.RESOLVED)
        vm.requestTransition(SupportTicketStatus.UNDER_REVIEW)
        assertEquals(SupportTicketStatus.UNDER_REVIEW, vm.state.value.pendingTransition)
    }

    @Test fun `reasonless transition applies directly with expected status`() = runTest(dispatcher) {
        val vm = vm(SupportTicketStatus.OPEN)
        whenever(repo.setStatus(any(), any(), anyOrNull(), anyOrNull())).thenReturn(Result.success(ticket(SupportTicketStatus.UNDER_REVIEW)))
        vm.requestTransition(SupportTicketStatus.UNDER_REVIEW); advanceUntilIdle()
        verify(repo).setStatus(eq("t1"), eq(SupportTicketStatus.UNDER_REVIEW), eq(null), eq(SupportTicketStatus.OPEN))
    }

    @Test fun `invalid transitions and closed tickets are rejected client side`() = runTest(dispatcher) {
        val closed = vm(SupportTicketStatus.CLOSED)
        closed.requestTransition(SupportTicketStatus.UNDER_REVIEW); advanceUntilIdle()
        verify(repo, times(0)).setStatus(any(), any(), anyOrNull(), anyOrNull())
        assertNull(closed.state.value.pendingTransition)
    }

    @Test fun `conflict from another manager shows a friendly error and refreshes`() = runTest(dispatcher) {
        val vm = vm(SupportTicketStatus.OPEN)
        whenever(repo.setStatus(any(), any(), anyOrNull(), anyOrNull())).thenReturn(Result.failure(Exception("TICKET_CHANGED:UNDER_REVIEW")))
        vm.requestTransition(SupportTicketStatus.UNDER_REVIEW); advanceUntilIdle()
        assertTrue(vm.state.value.actionError!!.contains("updated"))
        verify(repo, times(2)).getTicketDetail("t1")
    }

    @Test fun `reopen conflict explains the existing active ticket`() = runTest(dispatcher) {
        val vm = vm(SupportTicketStatus.RESOLVED)
        whenever(repo.setStatus(any(), any(), anyOrNull(), anyOrNull())).thenReturn(Result.failure(Exception("REOPEN_CONFLICT:abc")))
        vm.requestTransition(SupportTicketStatus.UNDER_REVIEW); vm.setReasonDraft("Student sent new evidence"); vm.confirmTransition(); advanceUntilIdle()
        assertTrue(vm.state.value.actionError!!.contains("Another active ticket"))
    }

    @Test fun `reply draft is kept when sending fails and cleared on success`() = runTest(dispatcher) {
        val vm = vm(SupportTicketStatus.OPEN)
        whenever(repo.respond(any(), any())).thenReturn(Result.failure(Exception("boom")))
        vm.setResponseDraft("hello"); vm.sendResponse(); advanceUntilIdle()
        assertEquals("hello", vm.state.value.responseDraft)
        whenever(repo.respond(any(), any())).thenReturn(Result.success(SupportTicketMessage("m", "t1", SupportMessageAuthor.MANAGEMENT, "hello", "c")))
        vm.sendResponse(); advanceUntilIdle()
        assertEquals("", vm.state.value.responseDraft)
    }

    @Test fun `internal note goes only to the note endpoint, never the student reply endpoint`() = runTest(dispatcher) {
        val vm = vm(SupportTicketStatus.OPEN)
        whenever(repo.addInternalNote(any(), any())).thenReturn(Result.success(SupportTicketInternalNote("n", "Me", "secret", "c")))
        vm.setNoteDraft("secret"); vm.addNote(); advanceUntilIdle()
        verify(repo).addInternalNote("t1", "secret")
        verify(repo, times(0)).respond(any(), any())
        assertEquals("", vm.state.value.noteDraft)
    }

    @Test fun `assignee list and linked reviews appear when the helper functions exist`() = runTest(dispatcher) {
        assignees = Result.success(listOf(SupportAssignee("a1", "Asha")))
        reviews = Result.success(SupportTicketReviews(emptyList(), null))
        val vm = vm(SupportTicketStatus.OPEN)
        assertEquals("Asha", vm.state.value.assignees!!.single().name)
        assertNotNull(vm.state.value.linkedReviews)
    }

    @Test fun `missing helper functions degrade gracefully instead of failing the screen`() = runTest(dispatcher) {
        val vm = vm(SupportTicketStatus.OPEN)
        assertNull(vm.state.value.assignees)
        assertNull(vm.state.value.linkedReviews)
        assertTrue(vm.state.value.detail is UiState.Success)
    }

    @Test fun `assigning to a chosen admin calls the server and refreshes`() = runTest(dispatcher) {
        val vm = vm(SupportTicketStatus.OPEN)
        whenever(repo.assign(any(), anyOrNull())).thenReturn(Result.success(ticket(SupportTicketStatus.OPEN)))
        vm.assignTo("a1"); advanceUntilIdle()
        verify(repo).assign("t1", "a1")
        verify(repo, times(2)).getTicketDetail("t1")
    }
}

private fun <T> anyOrNull(): T? = org.mockito.kotlin.anyOrNull()
