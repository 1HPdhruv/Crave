package com.srmfood.gag.feature.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.repository.AdminRepository
import com.srmfood.gag.domain.usecase.admin.ManagementInventoryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManagementInventoryViewModel @Inject constructor(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _allItems = MutableStateFlow<List<ManagementInventoryItem>>(emptyList())
    
    private val _uiState = MutableStateFlow<UiState<List<ManagementInventoryItem>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<ManagementInventoryItem>>> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(FilterType.ALL)
    val selectedFilter: StateFlow<FilterType> = _selectedFilter.asStateFlow()

    enum class FilterType {
        ALL, IN_STOCK, LOW_STOCK, OUT_OF_STOCK
    }

    init {
        loadInventory()
        
        // Combine all items with filters to produce the final filtered list
        viewModelScope.launch {
            combine(_allItems, _searchQuery, _selectedFilter) { items, query, filter ->
                var result = items
                
                // Apply search filter (food name or outlet name)
                if (query.isNotBlank()) {
                    result = result.filter { 
                        it.foodName.contains(query, ignoreCase = true) || 
                        it.outletName.contains(query, ignoreCase = true) 
                    }
                }
                
                // Apply stock filter
                result = when (filter) {
                    FilterType.ALL -> result
                    FilterType.IN_STOCK -> result.filter { it.stockStatus == ManagementInventoryItem.StockStatus.IN_STOCK }
                    FilterType.LOW_STOCK -> result.filter { it.stockStatus == ManagementInventoryItem.StockStatus.LOW_STOCK }
                    FilterType.OUT_OF_STOCK -> result.filter { it.stockStatus == ManagementInventoryItem.StockStatus.OUT_OF_STOCK }
                }
                
                result
            }.collect { filteredList ->
                // Only update UiState if it was already Success to avoid overwriting Loading/Error
                if (_uiState.value is UiState.Success) {
                    _uiState.value = UiState.Success(filteredList)
                }
            }
        }
    }

    private fun loadInventory() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            fetchInventory()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchInventory()
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchInventory() {
        adminRepository.getInventory().fold(
            onSuccess = { items ->
                _allItems.value = items
                _uiState.value = UiState.Success(items) // The combine block will immediately overwrite this with filtered results if needed
            },
            onFailure = { error ->
                _uiState.value = UiState.Error("Management data is temporarily unavailable. Please try again.")
            }
        )
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: FilterType) {
        _selectedFilter.value = filter
    }
}
