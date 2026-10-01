package com.srmfood.gag.feature.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.Outlet
import com.srmfood.gag.domain.repository.OutletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManagementOutletsViewModel @Inject constructor(
    private val outletRepository: OutletRepository
) : ViewModel() {

    private val _outletsState = MutableStateFlow<UiState<List<Outlet>>>(UiState.Loading)
    val outletsState: StateFlow<UiState<List<Outlet>>> = _outletsState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _currentFilter = MutableStateFlow("ALL")
    val currentFilter: StateFlow<String> = _currentFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadOutlets()
    }

    private fun loadOutlets() {
        viewModelScope.launch {
            _outletsState.value = UiState.Loading
            fetchOutlets()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchOutlets()
            _isRefreshing.value = false
        }
    }

    fun updateFilter(filter: String) {
        if (_currentFilter.value == filter) return
        _currentFilter.value = filter
        loadOutlets()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun submitSearch() {
        loadOutlets()
    }

    private suspend fun fetchOutlets() {
        val filter = if (_currentFilter.value == "ALL") null else _currentFilter.value
        val query = _searchQuery.value.takeIf { it.isNotBlank() }

        outletRepository.getAdminOutlets(
            statusFilter = filter,
            searchQuery = query
        ).fold(
            onSuccess = { _outletsState.value = UiState.Success(it) },
            onFailure = { _outletsState.value = UiState.Error("Management data is temporarily unavailable. Please try again.") }
        )
    }
}
