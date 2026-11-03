package com.srmfood.gag.feature.search

import androidx.lifecycle.SavedStateHandle
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.*
import com.srmfood.gag.domain.repository.CartRepository
import com.srmfood.gag.domain.repository.FoodRepository
import com.srmfood.gag.domain.repository.OutletRepository
import com.srmfood.gag.domain.usecase.cart.*
import com.srmfood.gag.domain.usecase.food.*
import com.srmfood.gag.domain.usecase.outlet.CheckOutletAvailabilityUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun food(name: String) = FoodItem(name, name, "d", null, 20.0, "o1", "Cafe", "Snacks", true, true, 5, 0.0, 0,
        emptyList(), emptyList(), emptyList(), null, false, false)

    /** Scripted food repository: every searchFood call is recorded and answered by [answer]. */
    private class FakeFood(val answer: suspend (FoodSearchFilter) -> Result<List<FoodItem>>) : FoodRepository by org.mockito.kotlin.mock() {
        val filters = mutableListOf<FoodSearchFilter>()
        override suspend fun searchFood(filter: FoodSearchFilter): Result<List<FoodItem>> { filters += filter; return answer(filter) }
        override suspend fun getCategories(): Result<List<com.srmfood.gag.domain.model.FoodCategory>> = Result.success(emptyList())
        override suspend fun getPopularFood(): Result<List<FoodItem>> = Result.success(emptyList())
        override suspend fun getAllFood(): Result<List<FoodItem>> = Result.success(emptyList())
    }

    private fun vm(repo: FoodRepository, handle: SavedStateHandle = SavedStateHandle()): SearchViewModel {
        val cart: CartRepository = mock(); val outlets: OutletRepository = mock()
        return SearchViewModel(SearchFoodUseCase(repo), GetCategoriesUseCase(repo), GetPopularFoodUseCase(repo), GetAllFoodUseCase(repo),
            AddToCartUseCase(cart, CheckOutletAvailabilityUseCase(outlets)), GetCartOutletIdUseCase(cart), ToggleFavoriteUseCase(repo), ClearCartUseCase(cart), handle)
    }

    @Test fun `a slow older response can never overwrite a newer search`() = runTest(dispatcher) {
        val slow = CompletableDeferred<Result<List<FoodItem>>>()
        val repo = FakeFood { f -> if (f.query == "pizza") slow.await() else Result.success(listOf(food("Burger"))) }
        val vm = vm(repo)
        advanceUntilIdle()
        vm.onQueryChanged("pizza"); advanceTimeBy(500)             // request 1 starts and hangs
        vm.onQueryChanged("burger"); advanceTimeBy(500); advanceUntilIdle()
        slow.complete(Result.success(listOf(food("Pizza"))))       // the old answer finally arrives
        advanceUntilIdle()
        val r = vm.uiState.value.results as UiState.Success
        assertEquals("Burger", r.data.single().name)
    }

    @Test fun `a failed search shows an error and retry recovers`() = runTest(dispatcher) {
        var fail = true
        val repo = FakeFood { if (fail) Result.failure(java.io.IOException("offline")) else Result.success(listOf(food("Dosa"))) }
        val vm = vm(repo, SavedStateHandle(mapOf("query" to "dosa")))
        advanceUntilIdle()
        assertTrue("state=${vm.uiState.value.results} calls=${repo.filters.size}", vm.uiState.value.results is UiState.Error)
        fail = false; vm.search(); advanceUntilIdle()
        assertTrue(vm.uiState.value.results is UiState.Success)
    }

    @Test fun `no matches is an empty state, not an error`() = runTest(dispatcher) {
        val vm = vm(FakeFood { Result.success(emptyList()) })
        advanceUntilIdle(); vm.onQueryChanged("zzzz"); advanceTimeBy(500); advanceUntilIdle()
        assertTrue(vm.uiState.value.results is UiState.Empty)
    }

    @Test fun `the route's initial query is shown in the field and searched`() = runTest(dispatcher) {
        val repo = FakeFood { Result.success(listOf(food("Dosa"))) }
        val vm = vm(repo, SavedStateHandle(mapOf("query" to "dosa")))
        advanceUntilIdle()
        assertEquals("dosa", vm.uiState.value.query)
        assertEquals("dosa", repo.filters.first().query)
    }

    @Test fun `changing a filter re-runs an active search but not an idle one`() = runTest(dispatcher) {
        val repo = FakeFood { Result.success(listOf(food("Dosa"))) }
        val idle = vm(repo); advanceUntilIdle()
        idle.onVegFilterChanged(true); advanceUntilIdle()
        assertTrue(repo.filters.isEmpty())
        idle.onQueryChanged("dosa"); advanceTimeBy(500); advanceUntilIdle()
        val before = repo.filters.size
        idle.onVegFilterChanged(true); advanceUntilIdle()
        assertEquals(before + 1, repo.filters.size)
        assertEquals(true, repo.filters.last().isVeg)
    }

    @Test fun `clearing the query drops in-flight results back to idle`() = runTest(dispatcher) {
        val slow = CompletableDeferred<Result<List<FoodItem>>>()
        val repo = FakeFood { slow.await() }
        val vm = vm(repo); advanceUntilIdle()
        vm.onQueryChanged("pizza"); advanceTimeBy(500)
        vm.onQueryChanged(""); advanceTimeBy(500); advanceUntilIdle()
        slow.complete(Result.success(listOf(food("Pizza")))); advanceUntilIdle()
        assertTrue(vm.uiState.value.results is UiState.Idle)
    }

    @Test fun `queries are trimmed and one character is not searched`() = runTest(dispatcher) {
        val repo = FakeFood { Result.success(emptyList()) }
        val vm = vm(repo); advanceUntilIdle()
        vm.onQueryChanged("a"); advanceTimeBy(500); advanceUntilIdle()
        assertTrue(repo.filters.isEmpty())
        vm.onQueryChanged("  tea  "); advanceTimeBy(500); advanceUntilIdle()
        assertEquals("tea", repo.filters.last().query)
    }
}
