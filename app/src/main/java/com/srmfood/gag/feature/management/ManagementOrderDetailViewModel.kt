package com.srmfood.gag.feature.management

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManagementOrderDetailViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val orderId: String = checkNotNull(savedStateHandle["orderId"])

    private val _orderState = MutableStateFlow<UiState<Order>>(UiState.Loading)
    val orderState: StateFlow<UiState<Order>> = _orderState.asStateFlow()

    init {
        loadOrder()
    }

    fun refresh() {
        loadOrder()
    }

    private fun loadOrder() {
        viewModelScope.launch {
            _orderState.value = UiState.Loading
            orderRepository.getOrderById(orderId).fold(
                onSuccess = { _orderState.value = UiState.Success(it) },
                onFailure = { _orderState.value = UiState.Error("Management data is temporarily unavailable. Please try again.") }
            )
        }
    }
}
