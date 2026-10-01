package com.srmfood.gag.feature.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.FoodCategory
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.repository.FoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManagementContentUiState(
    val popularFood: UiState<List<FoodItem>> = UiState.Loading,
    val recommendedFood: UiState<List<FoodItem>> = UiState.Loading,
    val categories: UiState<List<FoodCategory>> = UiState.Loading,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class ManagementContentViewModel @Inject constructor(
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManagementContentUiState())
    val uiState: StateFlow<ManagementContentUiState> = _uiState.asStateFlow()

    init {
        loadContent()
    }

    fun loadContent() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            // Load Popular Food
            foodRepository.getPopularFood().fold(
                onSuccess = { items ->
                    _uiState.update { it.copy(popularFood = UiState.Success(items)) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(popularFood = UiState.Error("Management data is temporarily unavailable. Please try again.")) }
                }
            )

            // Load Recommended Food
            foodRepository.getRecommendedFood().fold(
                onSuccess = { items ->
                    _uiState.update { it.copy(recommendedFood = UiState.Success(items)) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(recommendedFood = UiState.Error("Management data is temporarily unavailable. Please try again.")) }
                }
            )

            // Load Categories
            foodRepository.getCategories().fold(
                onSuccess = { cats ->
                    _uiState.update { it.copy(categories = UiState.Success(cats)) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(categories = UiState.Error("Management data is temporarily unavailable. Please try again.")) }
                }
            )
            
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
