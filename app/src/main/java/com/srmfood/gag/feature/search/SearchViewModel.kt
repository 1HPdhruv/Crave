package com.srmfood.gag.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.model.FoodSearchFilter
import com.srmfood.gag.domain.model.SortOption
import com.srmfood.gag.domain.usecase.cart.AddToCartUseCase
import com.srmfood.gag.domain.usecase.cart.GetCartOutletIdUseCase
import com.srmfood.gag.domain.usecase.cart.ClearCartUseCase
import com.srmfood.gag.domain.usecase.food.SearchFoodUseCase
import com.srmfood.gag.domain.usecase.food.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SearchUiEvent {
    data class ShowSnackbar(val message: String) : SearchUiEvent()
}

data class SearchUiState(
    val query: String = "",
    val results: UiState<List<FoodItem>> = UiState.Idle,
    val filterVegOnly: Boolean? = null,
    val filterMaxPrice: Double? = null,
    val filterMaxPrepTime: Int? = null,
    val filterMinRating: Double? = null,
    val selectedCategory: String? = null,
    val sortBy: SortOption = SortOption.RELEVANCE,
    val showFilters: Boolean = false,
    val cartOutletId: String? = null,
    val showMixedOutletDialog: Boolean = false,
    val pendingAddFoodItem: FoodItem? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchFoodUseCase: SearchFoodUseCase,
    private val addToCartUseCase: AddToCartUseCase,
    private val getCartOutletIdUseCase: GetCartOutletIdUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val clearCartUseCase: ClearCartUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SearchUiEvent>()
    val events: SharedFlow<SearchUiEvent> = _events.asSharedFlow()

    private val _queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            _queryFlow
                .debounce(400)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.length >= 2) search()
                    else if (query.isBlank()) _uiState.update { it.copy(results = UiState.Idle) }
                }
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
        _queryFlow.value = query
    }

    fun onVegFilterChanged(vegOnly: Boolean?) = _uiState.update { it.copy(filterVegOnly = vegOnly) }
    fun onMaxPriceChanged(price: Double?) = _uiState.update { it.copy(filterMaxPrice = price) }
    fun onMaxPrepTimeChanged(mins: Int?) = _uiState.update { it.copy(filterMaxPrepTime = mins) }
    fun onMinRatingChanged(rating: Double?) = _uiState.update { it.copy(filterMinRating = rating) }
    fun onCategorySelected(cat: String?) = _uiState.update { it.copy(selectedCategory = cat) }
    fun onSortChanged(sort: SortOption) = _uiState.update { it.copy(sortBy = sort) }
    fun toggleFilters() = _uiState.update { it.copy(showFilters = !it.showFilters) }

    fun search() {
        val state = _uiState.value
        val filter = FoodSearchFilter(
            query = state.query,
            category = state.selectedCategory,
            isVeg = state.filterVegOnly,
            maxPrice = state.filterMaxPrice,
            maxPrepTimeMinutes = state.filterMaxPrepTime,
            minRating = state.filterMinRating,
            sortBy = state.sortBy
        )
        viewModelScope.launch {
            _uiState.update { it.copy(results = UiState.Loading) }
            val result = searchFoodUseCase(filter)
            _uiState.update {
                it.copy(
                    results = result.fold(
                        onSuccess = { items -> if (items.isEmpty()) UiState.Empty else UiState.Success(items) },
                        onFailure = { err ->
                            android.util.Log.e("SearchViewModel", "Search RPC failed. Reason: ${err.message}", err)
                            UiState.Error("Unable to search right now. Please try again.")
                        }
                    )
                )
            }
        }
    }

    fun onAddToCartClicked(foodItem: FoodItem) {
        viewModelScope.launch {
            val cartOutletId = getCartOutletIdUseCase()
            if (cartOutletId != null && cartOutletId != foodItem.outletId) {
                // Different outlet — show dialog
                _uiState.update { it.copy(showMixedOutletDialog = true, pendingAddFoodItem = foodItem, cartOutletId = cartOutletId) }
            } else {
                addItemToCart(foodItem)
            }
        }
    }

    private fun addItemToCart(foodItem: FoodItem) {
        viewModelScope.launch {
            addToCartUseCase(foodItem, 1).onSuccess {
                _events.emit(SearchUiEvent.ShowSnackbar("Added ${foodItem.name} to cart"))
            }.onFailure {
                _events.emit(SearchUiEvent.ShowSnackbar("Failed to add: ${it.message}"))
            }
            _uiState.update { it.copy(pendingAddFoodItem = null) }
        }
    }

    fun dismissMixedOutletDialog() = _uiState.update { it.copy(showMixedOutletDialog = false, pendingAddFoodItem = null) }

    fun onClearAndAddCart() {
        viewModelScope.launch {
            clearCartUseCase().onSuccess {
                _uiState.value.pendingAddFoodItem?.let { foodItem ->
                    addItemToCart(foodItem)
                }
            }
            dismissMixedOutletDialog()
        }
    }

    fun toggleFavorite(foodItemId: String) {
        viewModelScope.launch { toggleFavoriteUseCase(foodItemId) }
    }
}
