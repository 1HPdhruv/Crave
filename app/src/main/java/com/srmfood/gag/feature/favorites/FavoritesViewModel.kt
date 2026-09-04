package com.srmfood.gag.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.usecase.food.GetFavoritesUseCase
import com.srmfood.gag.domain.usecase.food.RefreshFavoritesUseCase
import com.srmfood.gag.domain.usecase.food.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.srmfood.gag.domain.usecase.cart.AddToCartUseCase
import com.srmfood.gag.domain.usecase.cart.ClearCartUseCase
import com.srmfood.gag.domain.usecase.cart.GetCartOutletIdUseCase
import kotlinx.coroutines.flow.update

data class FavoritesUiState(
    val results: UiState<List<FoodItem>> = UiState.Loading,
    val showMixedOutletDialog: Boolean = false,
    val pendingAddFoodItem: FoodItem? = null,
    val cartOutletId: String? = null
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val refreshFavoritesUseCase: RefreshFavoritesUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val addToCartUseCase: AddToCartUseCase,
    private val getCartOutletIdUseCase: GetCartOutletIdUseCase,
    private val clearCartUseCase: ClearCartUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        // First fetch local favorites and start observing
        viewModelScope.launch {
            getFavoritesUseCase().collectLatest { items ->
                _uiState.update { it.copy(results = UiState.Success(items)) }
            }
        }
        
        // Then attempt to sync with backend
        viewModelScope.launch {
            refreshFavoritesUseCase()
        }
    }

    fun toggleFavorite(foodItemId: String) {
        viewModelScope.launch { toggleFavoriteUseCase(foodItemId) }
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
            addToCartUseCase(foodItem, 1)
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
}
