package com.srmfood.gag.feature.orders

import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.*
import com.srmfood.gag.domain.repository.*
import com.srmfood.gag.domain.usecase.order.*
import com.srmfood.gag.feature.management.ManagementLiveCampusViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class OrderFlowsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun order(id: String = "o1", status: OrderStatus = OrderStatus.PLACED) = Order(
        id, "#A1", "s", "v", "ou", "Outlet", emptyList(), 0.0, 0.0, 0.0, status, null, 0, null, "c", null, null, null,
        null, null, null, null, PaymentStatus.PAID, PaymentMethod.ONLINE, null, null
    )

    /** Fake order repo: cache flow + scripted refresh result (Mockito can't return inline Result<Unit> reliably). */
    private fun fake(cache: MutableStateFlow<List<Order>>, refresh: () -> Result<Unit>): OrderRepository {
        val base: OrderRepository = mock()
        return object : OrderRepository by base {
            override fun getOrders() = cache
            override suspend fun refreshOrders(): Result<Unit> = refresh()
        }
    }

    @Test fun `history shows an error, not an empty list, when the first refresh fails with nothing cached`() = runTest(dispatcher) {
        val repo = fake(MutableStateFlow(emptyList())) { Result.failure(java.io.IOException("offline")) }
        val vm = OrderHistoryViewModel(GetOrdersUseCase(repo), RefreshOrdersUseCase(repo))
        advanceUntilIdle()
        assertTrue("was ${vm.orders.value} err=${vm.refreshError.value}", vm.orders.value is UiState.Error)
    }

    @Test fun `history is not reported empty before the first server answer`() = runTest(dispatcher) {
        val repo = fake(MutableStateFlow(emptyList())) { Result.success(Unit) }
        val vm = OrderHistoryViewModel(GetOrdersUseCase(repo), RefreshOrdersUseCase(repo))
        assertTrue(vm.orders.value is UiState.Loading)
        advanceUntilIdle()
        assertTrue(vm.orders.value is UiState.Empty)
    }

    @Test fun `cached orders stay visible with a banner when a refresh fails`() = runTest(dispatcher) {
        val repo = fake(MutableStateFlow(listOf(order()))) { Result.failure(java.io.IOException("offline")) }
        val vm = OrderHistoryViewModel(GetOrdersUseCase(repo), RefreshOrdersUseCase(repo))
        advanceUntilIdle()
        assertTrue(vm.orders.value is UiState.Success)
        assertNotNull(vm.refreshError.value)
    }

    @Test fun `server changes reach the list after refresh`() = runTest(dispatcher) {
        val cache = MutableStateFlow(listOf(order(status = OrderStatus.PLACED)))
        val repo = fake(cache) { Result.success(Unit) }
        val vm = OrderHistoryViewModel(GetOrdersUseCase(repo), RefreshOrdersUseCase(repo))
        advanceUntilIdle()
        cache.value = listOf(order(status = OrderStatus.READY)) // what refreshOrders() writes to the cache
        advanceUntilIdle()
        assertEquals(OrderStatus.READY, (vm.orders.value as UiState.Success).data.single().status)
    }

    @Test fun `cancel failure is reported and the real status is reloaded`() = runTest(dispatcher) {
        val repo: OrderRepository = mock()
        whenever(repo.getOrderById("o1")).thenReturn(Result.success(order(status = OrderStatus.ACCEPTED)))
        whenever(repo.cancelOrder(any(), any())).thenReturn(Result.failure(Exception("This order can no longer be cancelled (it is now accepted).")))
        val reviews: ReviewRepository = mock()
        val vm = OrderDetailViewModel(GetOrderDetailsUseCase(repo), CancelOrderUseCase(repo), reviews)
        vm.cancelOrder("o1"); advanceUntilIdle()
        assertTrue(vm.cancelError.value!!.contains("no longer be cancelled"))
        assertEquals(OrderStatus.ACCEPTED, (vm.order.value as UiState.Success).data.status)
    }

    @Test fun `live campus does not turn a failed section into zeros`() = runTest(dispatcher) {
        val orders: OrderRepository = mock()
        val outlets: OutletRepository = mock()
        val food: FoodRepository = mock()
        val admin: AdminRepository = mock()
        whenever(orders.getAdminOrders(anyOrNull(), anyOrNull())).thenReturn(Result.failure(java.io.IOException("boom")))
        whenever(orders.getAdminPickupSlots(any())).thenReturn(Result.success(emptyList()))
        whenever(outlets.getAdminOutlets(anyOrNull(), anyOrNull())).thenReturn(Result.success(emptyList()))
        whenever(admin.getInventory()).thenReturn(Result.success(emptyList()))
        val vm = ManagementLiveCampusViewModel(orders, outlets, food, admin)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue(s.error!!.contains("orders"))
        assertTrue(s.hasData) // other sections loaded, so the page stays up with a banner
    }

    @Test fun `live campus reports total failure as an error`() = runTest(dispatcher) {
        val orders: OrderRepository = mock(); val outlets: OutletRepository = mock(); val food: FoodRepository = mock(); val admin: AdminRepository = mock()
        val boom = Result.failure<List<Nothing>>(java.io.IOException("boom"))
        whenever(orders.getAdminOrders(anyOrNull(), anyOrNull())).thenReturn(Result.failure(java.io.IOException("x")))
        whenever(orders.getAdminPickupSlots(any())).thenReturn(Result.failure(java.io.IOException("x")))
        whenever(outlets.getAdminOutlets(anyOrNull(), anyOrNull())).thenReturn(Result.failure(java.io.IOException("x")))
        whenever(admin.getInventory()).thenReturn(Result.failure(java.io.IOException("x")))
        val vm = ManagementLiveCampusViewModel(orders, outlets, food, admin)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.hasData)
    }
}
