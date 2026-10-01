package com.srmfood.gag.feature.management

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.model.Outlet
import com.srmfood.gag.domain.repository.FoodRepository
import com.srmfood.gag.domain.repository.OutletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoodDetailData(
    val foodItem: FoodItem,
    val outlet: Outlet?
)

@HiltViewModel
class ManagementFoodDetailViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val outletRepository: OutletRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val foodId: String = checkNotNull(savedStateHandle["foodId"])

    private val _detailState = MutableStateFlow<UiState<FoodDetailData>>(UiState.Loading)
    val detailState: StateFlow<UiState<FoodDetailData>> = _detailState.asStateFlow()

    init {
        loadData()
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _detailState.value = UiState.Loading
            
            val foodResult = foodRepository.getFoodById(foodId)
            
            foodResult.fold(
                onSuccess = { foodItem ->
                    val outlet = outletRepository.getOutletById(foodItem.outletId).getOrNull()
                    
                    _detailState.value = UiState.Success(
                        FoodDetailData(
                            foodItem = foodItem,
                            outlet = outlet
                        )
                    )
                },
                onFailure = { 
                    _detailState.value = UiState.Error("Management data is temporarily unavailable. Please try again.")
                }
            )
        }
    }
}
