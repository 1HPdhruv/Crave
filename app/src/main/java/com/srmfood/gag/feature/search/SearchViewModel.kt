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
import com.srmfood.gag.domain.usecase.food.GetPopularFoodUseCase
import com.srmfood.gag.domain.usecase.food.GetAllFoodUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.SavedStateHandle
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

import com.srmfood.gag.domain.model.FoodCategory
import com.srmfood.gag.domain.usecase.food.GetCategoriesUseCase

sealed class SearchUiEvent {
    data class ShowSnackbar(val message: String) : SearchUiEvent()
}

data class SearchUiState(
    val query: String = "",
    val results: UiState<List<FoodItem>> = UiState.Idle,
    val categories: UiState<List<FoodCategory>> = UiState.Loading,
    val campusFavorites: UiState<List<FoodItem>> = UiState.Loading,
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
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getPopularFoodUseCase: GetPopularFoodUseCase,
    private val getAllFoodUseCase: GetAllFoodUseCase,
    private val addToCartUseCase: AddToCartUseCase,
    private val getCartOutletIdUseCase: GetCartOutletIdUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val clearCartUseCase: ClearCartUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState(
        // the route's query argument used to be ignored: the field started empty while results used it
        query = savedStateHandle.get<String>("query").orEmpty(),
        selectedCategory = savedStateHandle.get<String>("category")
    ))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SearchUiEvent>()
    val events: SharedFlow<SearchUiEvent> = _events.asSharedFlow()

    private val _queryFlow = MutableStateFlow(savedStateHandle.get<String>("query") ?: "")

    init {
        // Fetch categories for the carousel
        viewModelScope.launch {
            val catResult = getCategoriesUseCase()
            _uiState.update { state ->
                state.copy(
                    categories = catResult.fold(
                        onSuccess = { cats -> if (cats.isEmpty()) UiState.Empty else UiState.Success(cats) },
                        onFailure = { err -> UiState.Error(err.message ?: "Failed to load categories") }
                    )
                )
            }
        }

        // Fetch campus favorites for explore discovery hub
        viewModelScope.launch {
            val popularResult = getPopularFoodUseCase()
            val items = if (popularResult.isSuccess && popularResult.getOrNull()?.isNotEmpty() == true) {
                popularResult.getOrNull()!!
            } else {
                getAllFoodUseCase().getOrNull() ?: emptyList()
            }
            
            val ranked = items
                .filter { it.isAvailable }
                .sortedWith(
                    compareByDescending<FoodItem> { it.isPopular }
                        .thenByDescending { it.totalReviews }
                        .thenByDescending { it.rating }
                        .thenBy { it.name }
                )
                .take(8)

            _uiState.update { state ->
                state.copy(
                    campusFavorites = if (ranked.isEmpty()) UiState.Empty else UiState.Success(ranked)
                )
            }
        }

        // A category or query passed through the route triggers a search immediately.
        if (_uiState.value.selectedCategory != null || _uiState.value.query.trim().length >= MIN_QUERY) {
            search()
        }

        viewModelScope.launch {
            _queryFlow
                .debounce(400)
                .distinctUntilChanged()
                .collect { query ->
                    val hasCategory = _uiState.value.selectedCategory != null
                    val q = query.trim()
                    if (q.length >= MIN_QUERY || (hasCategory && q.isBlank())) {
                        search()
                    } else if (!hasCategory) {
                        // Too short to search: drop any in-flight request so it can't resurrect old results.
                        searchJob?.cancel()
                        _uiState.update { it.copy(results = UiState.Idle) }
                    }
                }
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
        _queryFlow.value = query
    }

    /** Re-runs the current search after a filter/sort change, but only when a search is actually active. */
    private fun researchIfActive() {
        val s = _uiState.value
        if (s.selectedCategory != null || s.query.trim().length >= MIN_QUERY) search()
    }

    fun onVegFilterChanged(vegOnly: Boolean?) { _uiState.update { it.copy(filterVegOnly = vegOnly) }; researchIfActive() }
    fun onMaxPriceChanged(price: Double?) { _uiState.update { it.copy(filterMaxPrice = price) }; researchIfActive() }
    fun onMaxPrepTimeChanged(mins: Int?) { _uiState.update { it.copy(filterMaxPrepTime = mins) }; researchIfActive() }
    fun onMinRatingChanged(rating: Double?) { _uiState.update { it.copy(filterMinRating = rating) }; researchIfActive() }
    
    fun onCategorySelected(cat: String?) {
        _uiState.update { it.copy(selectedCategory = cat) }
        search() // Automatically trigger search when category changes
    }
    
    fun onSortChanged(sort: SortOption) { _uiState.update { it.copy(sortBy = sort) }; researchIfActive() }
    fun toggleFilters() = _uiState.update { it.copy(showFilters = !it.showFilters) }

    private var searchJob: kotlinx.coroutines.Job? = null

    /** Latest request wins: starting a new search cancels the previous one, so a slow old response can't overwrite newer results. */
    fun search() {
        val state = _uiState.value
        val filter = FoodSearchFilter(
            query = state.query.trim(),
            category = state.selectedCategory,
            isVeg = state.filterVegOnly,
            maxPrice = state.filterMaxPrice,
            maxPrepTimeMinutes = state.filterMaxPrepTime,
            minRating = state.filterMinRating,
            sortBy = state.sortBy
        )
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(results = UiState.Loading) }
            val result = searchFoodUseCase(filter)
            _uiState.update {
                it.copy(
                    results = result.fold(
                        onSuccess = { items -> if (items.isEmpty()) UiState.Empty else UiState.Success(items) },
                        onFailure = { err ->
                            com.srmfood.gag.core.common.SafeLog.e("SearchViewModel", "Search RPC failed. Reason: ${err.message}", err)
                            UiState.Error("Unable to search right now. Please try again.")
                        }
                    )
                )
            }
        }
    }

    companion object { const val MIN_QUERY = 2 }

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
                _events.emit(SearchUiEvent.ShowSnackbar(
                    if (it is com.srmfood.gag.domain.usecase.outlet.OutletUnavailableException) it.message ?: "This outlet can't take orders right now."
                    else "Couldn't add to cart: " + com.srmfood.gag.core.common.UiState.Error.userFacing(it.message ?: "please try again")))
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
