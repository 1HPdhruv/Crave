package com.srmfood.gag.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.FoodCategory
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.Outlet
import com.srmfood.gag.domain.model.User
import com.srmfood.gag.domain.repository.HostelAddress
import com.srmfood.gag.domain.repository.OrderingMode
import com.srmfood.gag.domain.repository.OrderingModeRepository
import com.srmfood.gag.domain.usecase.auth.GetCurrentUserUseCase
import com.srmfood.gag.domain.usecase.food.GetAllFoodUseCase
import com.srmfood.gag.domain.usecase.food.GetCategoriesUseCase
import com.srmfood.gag.domain.usecase.food.GetPopularFoodUseCase
import com.srmfood.gag.domain.usecase.food.GetRecommendedFoodUseCase
import com.srmfood.gag.domain.usecase.order.GetOrdersUseCase
import com.srmfood.gag.domain.usecase.outlet.GetOutletsUseCase
import com.srmfood.gag.domain.usecase.outlet.RefreshOutletsUseCase
import com.srmfood.gag.domain.usecase.cart.GetCartUseCase
import com.srmfood.gag.domain.usecase.cart.AddToCartUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "HomeViewModel"

sealed class HomeUiEvent {
    data class ShowSnackbar(val message: String) : HomeUiEvent()
}

data class HomeUiState(
    val user: User? = null,
    val outlets: UiState<List<Outlet>> = UiState.Loading,
    val popularFood: UiState<List<FoodItem>> = UiState.Loading,
    val recommendedFood: UiState<List<FoodItem>> = UiState.Loading,
    val categories: UiState<List<FoodCategory>> = UiState.Loading,
    val activeOrder: Order? = null,
    val cartItemCount: Int = 0,
    // ─── Ordering mode ───────────────────────────────────────────
    val orderingMode: OrderingMode = OrderingMode.PICKUP,
    val hostelAddress: HostelAddress = HostelAddress()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getOutletsUseCase: GetOutletsUseCase,
    private val refreshOutletsUseCase: RefreshOutletsUseCase,
    private val getPopularFoodUseCase: GetPopularFoodUseCase,
    private val getRecommendedFoodUseCase: GetRecommendedFoodUseCase,
    private val getAllFoodUseCase: GetAllFoodUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getOrdersUseCase: GetOrdersUseCase,
    private val getCartUseCase: GetCartUseCase,
    private val addToCartUseCase: AddToCartUseCase,
    private val syncCartUseCase: com.srmfood.gag.domain.usecase.cart.SyncCartUseCase,
    private val orderingModeRepository: OrderingModeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeUiEvent>()
    val events: SharedFlow<HomeUiEvent> = _events.asSharedFlow()

    init {
        observeUser()
        observeOutlets()   // Observe Room cache as a Flow
        observeActiveOrders()
        observeCart()
        observeOrderingMode()
        loadData()         // Trigger network fetch
    }

    private fun observeOrderingMode() {
        viewModelScope.launch {
            orderingModeRepository.orderingMode.collectLatest { mode ->
                _uiState.value = _uiState.value.copy(orderingMode = mode)
            }
        }
        viewModelScope.launch {
            orderingModeRepository.hostelAddress.collectLatest { address ->
                _uiState.value = _uiState.value.copy(hostelAddress = address)
            }
        }
    }

    fun setOrderingMode(mode: OrderingMode) {
        viewModelScope.launch { orderingModeRepository.setOrderingMode(mode) }
    }

    fun saveHostelAddress(address: HostelAddress) {
        viewModelScope.launch { orderingModeRepository.setHostelAddress(address) }
    }

    private fun observeCart() {
        viewModelScope.launch {
            // Force an initial sync so we don't overwrite pending items later
            kotlin.runCatching { syncCartUseCase() }
            getCartUseCase().collectLatest { cart ->
                _uiState.value = _uiState.value.copy(
                    cartItemCount = cart?.items?.sumOf { it.quantity } ?: 0
                )
            }
        }
    }

    fun addToCart(foodItem: FoodItem) {
        viewModelScope.launch {
            Log.d(TAG, "addToCart: Adding ${foodItem.name} (ID: ${foodItem.id}) to cart")
            addToCartUseCase(foodItem, quantity = 1).onFailure { e ->
                com.srmfood.gag.core.common.SafeLog.e(TAG, "addToCart: Failed to add item to cart", e)
                _events.emit(HomeUiEvent.ShowSnackbar(
                    if (e is com.srmfood.gag.domain.usecase.outlet.OutletUnavailableException) e.message ?: "This outlet can't take orders right now."
                    else "Couldn't add to cart: " + com.srmfood.gag.core.common.UiState.Error.userFacing(e.message ?: "please try again")))
            }.onSuccess {
                Log.d(TAG, "addToCart: Successfully added to cart. Total items: ${it.items.size}")
                _events.emit(HomeUiEvent.ShowSnackbar("Added ${foodItem.name} to cart"))
            }
        }
    }

    private fun observeUser() {
        viewModelScope.launch {
            getCurrentUserUseCase().collectLatest { user ->
                _uiState.value = _uiState.value.copy(user = user)
            }
        }
    }

    /** Outlet refresh outcome; the Room observer must not overwrite a failure with "loading" or hide an empty result. */
    private enum class OutletRefresh { PENDING, SUCCEEDED, FAILED }
    private var outletRefresh = OutletRefresh.PENDING

    /** Observe Room cache — updates automatically once refreshOutlets() writes to DB. */
    private fun observeOutlets() {
        viewModelScope.launch {
            getOutletsUseCase().collectLatest { outlets ->
                Log.d(TAG, "Outlets from Room: ${outlets.size}")
                val targetNames = listOf("Butty", "Evergreen", "Sohana", "Chettinad", "S.R. Chat")
                val featured = targetNames.mapNotNull { target ->
                    outlets.find { it.name.contains(target, ignoreCase = true) }
                }.distinctBy { it.id }

                // Curated names first; if none of them exist in this database, show the real outlets instead of "empty".
                val shown = featured.ifEmpty { outlets.sortedBy { it.name }.take(6) }
                if (shown.isEmpty()) {
                    when (outletRefresh) {
                        OutletRefresh.PENDING -> _uiState.value = _uiState.value.copy(outlets = UiState.Loading)
                        OutletRefresh.SUCCEEDED -> _uiState.value = _uiState.value.copy(outlets = UiState.Empty)
                        OutletRefresh.FAILED -> Unit   // keep the error that loadData() reported
                    }
                } else {
                    _uiState.value = _uiState.value.copy(outlets = UiState.Success(shown))
                }
            }
        }
    }

    private fun observeActiveOrders() {
        viewModelScope.launch {
            getOrdersUseCase().collectLatest { orders ->
                _uiState.value = _uiState.value.copy(
                    activeOrder = orders.firstOrNull { it.status.isActive }
                )
            }
        }
    }

    fun loadData() {
        // Refresh outlets from Supabase into Room — the Flow above will pick it up automatically
        viewModelScope.launch {
            Log.d(TAG, "loadData: refreshing outlets from Supabase")
            outletRefresh = OutletRefresh.PENDING
            refreshOutletsUseCase().onFailure { e ->
                com.srmfood.gag.core.common.SafeLog.e(TAG, "loadData: refreshOutlets failed", e)
                outletRefresh = OutletRefresh.FAILED
                // Only replace the list if nothing cached is showing; never put raw exception text on screen.
                if (_uiState.value.outlets !is UiState.Success) {
                    _uiState.value = _uiState.value.copy(outlets = UiState.Error(homeLoadErrorMessage(e)))
                }
            }.onSuccess {
                Log.d(TAG, "loadData: refreshOutlets succeeded with ${it.size} outlets")
                outletRefresh = OutletRefresh.SUCCEEDED
                if (it.isEmpty()) _uiState.value = _uiState.value.copy(outlets = UiState.Empty)
            }
        }

        // Load all food once and filter for Popular and Recommended
        viewModelScope.launch {
            Log.d(TAG, "loadData: loading all food for popular & recommended filtering")
            val allResult = getAllFoodUseCase()
            allResult.onSuccess { allFood ->
                // Popular (Campus Picks)
                // Items flagged is_popular in the database come first; the curated names are only a fallback.
                val flagged = allFood.filter { it.isPopular && it.isAvailable }
                val popularTargets = listOf("Chicken Biryani", "Cheese Bread Omelet", "Watermelon")
                val curated = popularTargets.mapNotNull { search ->
                    allFood.find { it.name.equals(search, ignoreCase = true) }
                        ?: allFood.find { it.name.contains(search, ignoreCase = true) }
                }
                val popularList = (flagged + curated).distinctBy { it.id }.take(5)

                // Categories dynamically built from real active food
                val activeCategories = allFood
                    .map { it.category }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .map { catName -> 
                        FoodCategory(id = catName, name = catName, emoji = "", imageUrl = null)
                    }

                _uiState.value = _uiState.value.copy(
                    popularFood = if (popularList.isEmpty()) UiState.Empty else UiState.Success(popularList),
                    categories = if (activeCategories.isEmpty()) UiState.Empty else UiState.Success(activeCategories)
                )

                // Recommended
                val recTargets = listOf(
                    "Chicken Biryani",
                    "Schezwan Chicken",
                    "Chicken Rice/Noodles",
                    "Panner Rice/Noodles",
                    "Masala Dosa",
                    "Bread Omelet"
                )
                val recList = recTargets.mapNotNull { search ->
                    allFood.find { it.name.equals(search, ignoreCase = true) }
                        ?: allFood.find { it.name.contains(search, ignoreCase = true) }
                }.distinctBy { it.id }.take(6)

                _uiState.value = _uiState.value.copy(
                    recommendedFood = if (recList.isEmpty()) UiState.Empty else UiState.Success(recList)
                )
            }.onFailure { e ->
                com.srmfood.gag.core.common.SafeLog.e(TAG, "loadData: getAllFood failed", e)
                val msg = homeLoadErrorMessage(e)
                _uiState.value = _uiState.value.copy(
                    popularFood = UiState.Error(msg),
                    recommendedFood = UiState.Error(msg),
                    categories = UiState.Error(msg)          // used to stay "loading" forever
                )
            }
        }
    }
}


/**
 * What the Home screen shows when a Supabase request fails. Never the raw exception text: supabase-kt messages
 * contain request headers (including the access token) and SQL hints that mean nothing to a student.
 */
internal fun homeLoadErrorMessage(e: Throwable?): String {
    val raw = e?.message.orEmpty()
    return when {
        e is java.io.IOException || "Unable to resolve host" in raw || "timeout" in raw.lowercase() ->
            "Couldn't reach CRAVE. Check your connection and try again."
        "permission denied" in raw || "42501" in raw || "JWT" in raw ->
            "The menu isn't available right now. Please try again later."
        "schema cache" in raw || "PGRST2" in raw ->
            "The menu service is being updated. Please try again shortly."
        else -> "Something went wrong while loading. Please try again."
    }
}
