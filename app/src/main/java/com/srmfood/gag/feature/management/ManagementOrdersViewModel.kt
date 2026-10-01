package com.srmfood.gag.feature.management

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
class ManagementOrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _ordersState = MutableStateFlow<UiState<List<Order>>>(UiState.Loading)
    val ordersState: StateFlow<UiState<List<Order>>> = _ordersState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _currentFilter = MutableStateFlow("ALL")
    val currentFilter: StateFlow<String> = _currentFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadOrders()
    }

    private fun loadOrders() {
        viewModelScope.launch {
            _ordersState.value = UiState.Loading
            fetchOrders()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchOrders()
            _isRefreshing.value = false
        }
    }

    fun updateFilter(filter: String) {
        if (_currentFilter.value == filter) return
        _currentFilter.value = filter
        loadOrders() // Real server-side filtering
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        // Normally we'd debounce here, but for simplicity we can wait for submit or just load directly.
        // We'll require submit for search in the UI, or debounce it if we do it here.
    }

    fun submitSearch() {
        loadOrders()
    }

    private suspend fun fetchOrders() {
        val filter = if (_currentFilter.value == "ALL") null else _currentFilter.value
        val query = _searchQuery.value.takeIf { it.isNotBlank() }

        orderRepository.getAdminOrders(
            statusFilter = filter,
            searchQuery = query
        ).fold(
            onSuccess = { _ordersState.value = UiState.Success(it) },
            onFailure = { _ordersState.value = UiState.Error("Management data is temporarily unavailable. Please try again.") }
        )
    }
}
