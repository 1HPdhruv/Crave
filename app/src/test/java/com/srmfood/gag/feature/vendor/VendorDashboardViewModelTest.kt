package com.srmfood.gag.feature.vendor

import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.*
import com.srmfood.gag.domain.repository.OrderRepository
import com.srmfood.gag.domain.usecase.order.*
import com.srmfood.gag.feature.vendor.dashboard.VendorDashboardViewModel
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

@OptIn(ExperimentalCoroutinesApi::class)
class VendorDashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun order(status: OrderStatus) = Order(
        "o1", "#A1", "s", "v", "ou", "Outlet", emptyList(), 0.0, 0.0, 0.0, status, null, 0, null, "c", null, null, null,
        null, null, null, null, PaymentStatus.PAID, PaymentMethod.ONLINE, null, null
    )

    private class FakeRepo(var list: () -> Result<List<Order>>, var accept: () -> Result<Order>) :
        OrderRepository by org.mockito.kotlin.mock() {
        var listCalls = 0
        override suspend fun getVendorOrders(status: OrderStatus?): Result<List<Order>> { listCalls++; return list() }
        override suspend fun acceptOrder(orderId: String): Result<Order> = accept()
    }

    private fun vm(repo: OrderRepository) = VendorDashboardViewModel(
        GetVendorOrdersUseCase(repo), AcceptOrderUseCase(repo), RejectOrderUseCase(repo), StartPreparingUseCase(repo), MarkReadyUseCase(repo)
    )

    @Test fun `a failed accept is reported and the list is reloaded to the real state`() = runTest(dispatcher) {
        val repo = FakeRepo({ Result.success(listOf(order(OrderStatus.PLACED))) }, { Result.failure(Exception("Invalid transition")) })
        val vm = vm(repo); advanceUntilIdle()
        vm.accept("o1"); advanceUntilIdle()
        assertEquals("Invalid transition", vm.actionError.value)
        assertEquals(2, repo.listCalls)
    }

    @Test fun `background refresh keeps the list when the server hiccups`() = runTest(dispatcher) {
        var fail = false
        val repo = FakeRepo({ if (fail) Result.failure(java.io.IOException("offline")) else Result.success(listOf(order(OrderStatus.PLACED))) }, { Result.success(order(OrderStatus.ACCEPTED)) })
        val vm = vm(repo); advanceUntilIdle()
        fail = true
        vm.loadOrders(silent = true); advanceUntilIdle()
        assertTrue(vm.orders.value is UiState.Success)
    }

    @Test fun `a foreground load failure shows an error`() = runTest(dispatcher) {
        val repo = FakeRepo({ Result.failure(java.io.IOException("offline")) }, { Result.success(order(OrderStatus.ACCEPTED)) })
        val vm = vm(repo); advanceUntilIdle()
        assertTrue(vm.orders.value is UiState.Error)
    }

    @Test fun `a new order appears on the next background refresh`() = runTest(dispatcher) {
        var orders = emptyList<Order>()
        val repo = FakeRepo({ Result.success(orders) }, { Result.success(order(OrderStatus.ACCEPTED)) })
        val vm = vm(repo); advanceUntilIdle()
        assertTrue(vm.orders.value is UiState.Empty)
        orders = listOf(order(OrderStatus.PLACED))
        vm.loadOrders(silent = true); advanceUntilIdle()
        assertTrue(vm.orders.value is UiState.Success)
    }
}
