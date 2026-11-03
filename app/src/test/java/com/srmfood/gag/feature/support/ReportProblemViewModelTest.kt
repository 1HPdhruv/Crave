package com.srmfood.gag.feature.support

import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.SupportTicket
import com.srmfood.gag.domain.model.SupportTicketCategory
import com.srmfood.gag.domain.model.SupportTicketPriority
import com.srmfood.gag.domain.model.SupportTicketStatus
import com.srmfood.gag.domain.repository.OrderRepository
import com.srmfood.gag.domain.repository.SupportTicketRepository
import com.srmfood.gag.domain.usecase.order.GetOrderDetailsUseCase
import kotlinx.coroutines.CompletableDeferred
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
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ReportProblemViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val ids = FakeIds()
    private val repo: SupportTicketRepository = mock()
    private val orders: OrderRepository = mock()

    class FakeIds : SupportRequestIdStore {
        val map = mutableMapOf<String, String>()
        var created = 0
        override fun getOrCreate(key: String) = map.getOrPut(key) { created++; "00000000-0000-0000-0000-00000000000$created" }
        override fun clear(key: String) { map.remove(key) }
    }

    private fun ticket() = SupportTicket(
        "t1", "TKT-1", "s", "o1", "ou1", null, SupportTicketCategory.ORDER, "Cold food", "desc desc desc",
        SupportTicketPriority.NORMAL, SupportTicketStatus.OPEN, null, "c", "u", null, null
    )

    private fun order() = Order(
        id = "o1", orderNumber = "#A1", userId = "s", vendorId = "v", outletId = "ou1", outletName = "Outlet",
        items = emptyList(), subtotal = 0.0, tax = 0.0, total = 0.0, status = com.srmfood.gag.domain.model.OrderStatus.PICKED_UP,
        pickupSlot = null, estimatedPrepMinutes = 0, actualPrepMinutes = null, createdAt = "c", placedAt = null,
        acceptedAt = null, preparingAt = null, readyAt = null, pickedUpAt = null, cancelledAt = null,
        cancellationReason = null, paymentStatus = com.srmfood.gag.domain.model.PaymentStatus.PAID,
        paymentMethod = com.srmfood.gag.domain.model.PaymentMethod.ONLINE, specialInstructions = null, qrToken = null
    )

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private suspend fun readyVm(): ReportProblemViewModel {
        whenever(orders.getOrderById("o1")).thenReturn(Result.success(order()))
        val vm = ReportProblemViewModel(GetOrderDetailsUseCase(orders), repo, ids)
        vm.load("o1")
        dispatcher.scheduler.advanceUntilIdle()
        vm.setSubject("Cold food"); vm.setDescription("The food was cold when I picked it up")
        return vm
    }

    @Test fun `invalid form does not call the server`() = runTest(dispatcher) {
        val vm = readyVm()
        vm.setSubject("x")
        vm.submit(); advanceUntilIdle()
        verify(repo, times(0)).createTicket(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any())
        assertTrue(vm.state.value.showValidation)
    }

    @Test fun `rapid repeated taps create exactly one request`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Result<SupportTicket>>()
        var calls = 0
        val gated = object : SupportTicketRepository by repo {
            override suspend fun createTicket(
                category: SupportTicketCategory, subject: String, description: String,
                orderId: String?, foodItemId: String?, outletId: String?, clientRequestId: String
            ): Result<SupportTicket> { calls++; return gate.await() }
        }
        whenever(orders.getOrderById("o1")).thenReturn(Result.success(order()))
        val vm = ReportProblemViewModel(GetOrderDetailsUseCase(orders), gated, ids)
        vm.load("o1"); dispatcher.scheduler.advanceUntilIdle()
        vm.setSubject("Cold food"); vm.setDescription("The food was cold when I picked it up")
        vm.submit(); vm.submit(); vm.submit()
        assertTrue(vm.state.value.isSubmitting)
        gate.complete(Result.success(ticket()))
        advanceUntilIdle()
        assertEquals(1, calls)
        assertNotNull(vm.state.value.createdTicket)
    }

    @Test fun `request id survives a retryable failure and is cleared on success`() = runTest(dispatcher) {
        whenever(repo.createTicket(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(Result.failure(java.io.IOException("timeout")))
            .thenReturn(Result.success(ticket()))
        val vm = readyVm()
        vm.submit(); advanceUntilIdle()
        assertNotNull(vm.state.value.submitError)
        val firstId = ids.map.values.single()
        vm.submit(); advanceUntilIdle()
        assertEquals(1, ids.created) // same id reused for the retry
        assertTrue(ids.map.isEmpty()) // cleared once the server confirmed
        assertEquals(firstId, "00000000-0000-0000-0000-000000000001")
        assertNotNull(vm.state.value.createdTicket)
    }

    @Test fun `already open surfaces the existing ticket and drops the id`() = runTest(dispatcher) {
        val existing = "11111111-2222-3333-4444-555555555555"
        whenever(repo.createTicket(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(Result.failure(Exception("TICKET_ALREADY_OPEN:$existing")))
        val vm = readyVm()
        vm.submit(); advanceUntilIdle()
        assertEquals(existing, vm.state.value.existingTicketId)
        assertTrue(vm.state.value.submitError!!.contains("already have an open ticket"))
        assertTrue(ids.map.isEmpty())
    }

    @Test fun `outlet is never sent by the client`() = runTest(dispatcher) {
        whenever(repo.createTicket(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(Result.success(ticket()))
        val vm = readyVm()
        vm.submit(); advanceUntilIdle()
        verify(repo).createTicket(eq(SupportTicketCategory.ORDER), any(), any(), eq("o1"), eq(null), eq(null), any())
    }
}

private fun <T> anyOrNull(): T? = org.mockito.kotlin.anyOrNull()
