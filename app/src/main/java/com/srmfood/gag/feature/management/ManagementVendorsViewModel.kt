package com.srmfood.gag.feature.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.admin.VendorProfile
import com.srmfood.gag.domain.repository.ManagementVendorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManagementVendorsViewModel @Inject constructor(
    private val vendorRepository: ManagementVendorRepository
) : ViewModel() {

    private val _vendorsState = MutableStateFlow<UiState<List<VendorProfile>>>(UiState.Loading)
    val vendorsState: StateFlow<UiState<List<VendorProfile>>> = _vendorsState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _currentFilter = MutableStateFlow("ALL")
    val currentFilter: StateFlow<String> = _currentFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadVendors()
    }

    private fun loadVendors() {
        viewModelScope.launch {
            _vendorsState.value = UiState.Loading
            fetchVendors()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchVendors()
            _isRefreshing.value = false
        }
    }

    fun updateFilter(filter: String) {
        if (_currentFilter.value == filter) return
        _currentFilter.value = filter
        loadVendors()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun submitSearch() {
        loadVendors()
    }

    private suspend fun fetchVendors() {
        val filter = if (_currentFilter.value == "ALL") null else _currentFilter.value
        val query = _searchQuery.value.takeIf { it.isNotBlank() }

        vendorRepository.getVendors(
            roleFilter = filter,
            searchQuery = query
        ).fold(
            onSuccess = { _vendorsState.value = UiState.Success(it) },
            onFailure = { _vendorsState.value = UiState.Error("Management data is temporarily unavailable. Please try again.") }
        )
    }
}
