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
    val cartItemCount: Int = 0
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
    private val syncCartUseCase: com.srmfood.gag.domain.usecase.cart.SyncCartUseCase
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
        loadData()         // Trigger network fetch
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
                Log.e(TAG, "addToCart: Failed to add item to cart: ${e.message}")
                _events.emit(HomeUiEvent.ShowSnackbar("Failed to add to cart: ${e.message}"))
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

    /** Observe Room cache — updates automatically once refreshOutlets() writes to DB. */
    private fun observeOutlets() {
        viewModelScope.launch {
            getOutletsUseCase().collectLatest { outlets ->
                Log.d(TAG, "Outlets from Room: ${outlets.size}")
                _uiState.value = _uiState.value.copy(
                    outlets = if (outlets.isEmpty()) UiState.Loading else UiState.Success(outlets)
                )
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
            refreshOutletsUseCase().onFailure { e ->
                Log.e(TAG, "loadData: refreshOutlets failed: ${e.message}")
                _uiState.value = _uiState.value.copy(outlets = UiState.Error(e.message ?: "Failed to load outlets"))
            }.onSuccess {
                Log.d(TAG, "loadData: refreshOutlets succeeded with ${it.size} outlets")
            }
        }

        // Popular food — with fallback to all food if no items are flagged is_popular
        viewModelScope.launch {
            Log.d(TAG, "loadData: loading popular food")
            val popularResult = getPopularFoodUseCase()
            val popularList = popularResult.getOrElse { emptyList() }
            if (popularList.isNotEmpty()) {
                Log.d(TAG, "loadData: ${popularList.size} popular items found")
                _uiState.value = _uiState.value.copy(popularFood = UiState.Success(popularList))
            } else {
                // Fallback: show all available food items
                Log.d(TAG, "loadData: no is_popular items, falling back to getAllFood()")
                val allResult = getAllFoodUseCase()
                _uiState.value = _uiState.value.copy(
                    popularFood = allResult.fold(
                        onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Success(it) },
                        onFailure = { UiState.Error(it.message ?: "Failed to load food") }
                    )
                )
            }
        }

        // Recommended food — with fallback to popular/all food if no items are flagged is_recommended
        viewModelScope.launch {
            Log.d(TAG, "loadData: loading recommended food")
            val recResult = getRecommendedFoodUseCase()
            val recList = recResult.getOrElse { emptyList() }
            if (recList.isNotEmpty()) {
                Log.d(TAG, "loadData: ${recList.size} recommended items found")
                _uiState.value = _uiState.value.copy(recommendedFood = UiState.Success(recList))
            } else {
                // Fallback: show all available food items (same source as popular if no flags set)
                Log.d(TAG, "loadData: no is_recommended items, falling back to getAllFood()")
                val allResult = getAllFoodUseCase()
                _uiState.value = _uiState.value.copy(
                    recommendedFood = allResult.fold(
                        onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Success(it) },
                        onFailure = { UiState.Error(it.message ?: "Failed to load food") }
                    )
                )
            }
        }

        // Categories — direct Supabase query, no cache layer needed
        viewModelScope.launch {
            Log.d(TAG, "loadData: loading categories")
            val catResult = getCategoriesUseCase()
            _uiState.value = _uiState.value.copy(
                categories = catResult.fold(
                    onSuccess = { cats ->
                        Log.d(TAG, "loadData: ${cats.size} categories loaded")
                        if (cats.isEmpty()) UiState.Empty else UiState.Success(cats)
                    },
                    onFailure = { UiState.Error(it.message ?: "Failed to load categories") }
                )
            )
        }
    }
}
