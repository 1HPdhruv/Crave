package com.srmfood.gag.domain

import com.srmfood.gag.domain.model.*
import com.srmfood.gag.domain.repository.CartRepository
import com.srmfood.gag.domain.repository.OutletRepository
import com.srmfood.gag.domain.usecase.cart.AddToCartUseCase
import com.srmfood.gag.domain.usecase.outlet.*
import com.srmfood.gag.feature.management.outletActionErrorMessage
import com.srmfood.gag.feature.support.ticketIdFromDeepLink
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OutletAvailabilityTest {
    private fun outlet(status: OutletOrderingStatus, active: Boolean = true) = Outlet(
        "o1", "Test Outlet", "d", null, OutletLocation("", "", "", null, null), true,
        OperatingHours("08:00", "22:00", emptyList()), 0, 0, emptyList(), 0.0, 0, "v", active, null, status
    )
    private val repo: OutletRepository = mock()

    @Test fun `status semantics - flagged still orders, unknown fails safe`() {
        assertTrue(OutletOrderingStatus.ACTIVE.acceptsOrders)
        assertTrue(OutletOrderingStatus.FLAGGED.acceptsOrders)
        assertFalse(OutletOrderingStatus.SUSPENDED.acceptsOrders)
        assertFalse(OutletOrderingStatus.DEACTIVATED.acceptsOrders)
        assertFalse(OutletOrderingStatus.UNKNOWN.acceptsOrders)
        assertEquals(OutletOrderingStatus.ACTIVE, OutletOrderingStatus.fromString(null)) // column not deployed yet
        assertEquals(OutletOrderingStatus.UNKNOWN, OutletOrderingStatus.fromString("PAUSED"))
        assertFalse(outlet(OutletOrderingStatus.ACTIVE, active = false).acceptsOrders) // is_active still respected
    }

    @Test fun `suspension is independent of opening hours`() {
        val closedButActive = outlet(OutletOrderingStatus.ACTIVE).copy(isOpen = false)
        assertTrue(closedButActive.acceptsOrders) // pre-orders for later slots stay possible
    }

    @Test fun `availability check reads the server and blocks suspended outlets`() = runTest {
        whenever(repo.fetchOutlet("o1")).thenReturn(Result.success(outlet(OutletOrderingStatus.SUSPENDED)))
        val r = CheckOutletAvailabilityUseCase(repo)("o1")
        assertTrue(r is OutletAvailability.Unavailable)
        assertEquals(OutletOrderingStatus.SUSPENDED, (r as OutletAvailability.Unavailable).status)
    }

    @Test fun `unreachable server does not block (server still enforces)`() = runTest {
        whenever(repo.fetchOutlet("o1")).thenReturn(Result.failure(java.io.IOException("offline")))
        assertEquals(OutletAvailability.Available(verified = false), CheckOutletAvailabilityUseCase(repo)("o1"))
    }

    @Test fun `inactive outlet counts as unavailable even with ACTIVE ordering status`() = runTest {
        whenever(repo.fetchOutlet("o1")).thenReturn(Result.success(outlet(OutletOrderingStatus.ACTIVE, active = false)))
        assertTrue(CheckOutletAvailabilityUseCase(repo)("o1") is OutletAvailability.Unavailable)
    }

    private val food = FoodItem("f1", "Samosa", "d", null, 20.0, "o1", "Test Outlet", "Snacks", true, true, 5, 0.0, 0,
        emptyList(), emptyList(), emptyList(), null, false, false)

    @Test fun `add to cart is refused for a suspended outlet and never touches the cart`() = runTest {
        whenever(repo.fetchOutlet("o1")).thenReturn(Result.success(outlet(OutletOrderingStatus.SUSPENDED)))
        val cart: CartRepository = mock()
        val r = AddToCartUseCase(cart, CheckOutletAvailabilityUseCase(repo))(food, 1)
        val e = r.exceptionOrNull()
        assertTrue(e is OutletUnavailableException)
        assertEquals(OutletOrderingStatus.SUSPENDED, (e as OutletUnavailableException).status)
        verify(cart, times(0)).addToCart(any(), any(), anyOrNull(), any(), any(), any(), any(), any(), any(), anyOrNull())
    }

    @Test fun `add to cart proceeds when the outlet is reactivated`() = runTest {
        whenever(repo.fetchOutlet("o1")).thenReturn(Result.success(outlet(OutletOrderingStatus.ACTIVE)))
        val cart: CartRepository = mock()
        whenever(cart.addToCart(any(), any(), anyOrNull(), any(), any(), any(), any(), any(), any(), anyOrNull()))
            .thenReturn(Result.failure(Exception("reached repository")))
        val r = AddToCartUseCase(cart, CheckOutletAvailabilityUseCase(repo))(food, 1)
        assertEquals("reached repository", r.exceptionOrNull()?.message)
    }

    @Test fun `student message never mentions internal reasons and differs per status`() {
        val s = OutletOrderingStatus.SUSPENDED.studentMessage("Cafe")
        val d = OutletOrderingStatus.DEACTIVATED.studentMessage("Cafe")
        assertTrue(s.contains("temporarily")); assertTrue(d.contains("no longer"))
        assertEquals("", OutletOrderingStatus.FLAGGED.studentMessage("Cafe")) // flag is internal-only
    }

    @Test fun `server rejection text is recognised`() {
        assertTrue(isOutletSuspendedError("ERROR: OUTLET_ORDERING_SUSPENDED: this outlet is not accepting orders."))
        assertFalse(isOutletSuspendedError("Outlet is temporarily closed right now."))
    }

    @Test fun `management actions follow the transition table`() {
        assertEquals(setOf(OutletOrderingAction.FLAG, OutletOrderingAction.SUSPEND, OutletOrderingAction.DEACTIVATE),
            OutletOrderingAction.availableFrom(OutletOrderingStatus.ACTIVE).toSet())
        assertEquals(setOf(OutletOrderingAction.REACTIVATE), OutletOrderingAction.availableFrom(OutletOrderingStatus.DEACTIVATED).toSet())
        assertTrue(OutletOrderingAction.availableFrom(OutletOrderingStatus.UNKNOWN).isEmpty())
        assertTrue(OutletOrderingAction.SUSPEND.requiresNameConfirmation(OutletOrderingStatus.ACTIVE))
        assertFalse(OutletOrderingAction.FLAG.requiresNameConfirmation(OutletOrderingStatus.ACTIVE))
        assertFalse(OutletOrderingAction.REACTIVATE.requiresNameConfirmation(OutletOrderingStatus.SUSPENDED))
        assertTrue(OutletOrderingAction.REACTIVATE.requiresNameConfirmation(OutletOrderingStatus.DEACTIVATED))
        assertTrue(OutletOrderingAction.SUSPEND.minReasonLength > OutletOrderingAction.FLAG.minReasonLength)
    }

    @Test fun `management action errors are friendly`() {
        assertTrue(outletActionErrorMessage(Exception("CONFIRMATION_REQUIRED")).contains("exact name"))
        assertTrue(outletActionErrorMessage(Exception("Management access required.")).contains("administrator"))
        assertTrue(outletActionErrorMessage(Exception("PGRST202 Could not find the function")).contains("isn't available"))
    }

    @Test fun `ticket deep links route to the ticket and ignore other links`() {
        val id = "11111111-2222-3333-4444-555555555555"
        assertEquals(id, ticketIdFromDeepLink("ticket:$id:msg:99999999-2222-3333-4444-555555555555"))
        assertEquals(id, ticketIdFromDeepLink("ticket:$id:status:RESOLVED:1700000000000000"))
        assertNull(ticketIdFromDeepLink("order:$id"))
        assertNull(ticketIdFromDeepLink("ticket:not-a-uuid:msg:1"))
        assertNull(ticketIdFromDeepLink(null))
    }
}
