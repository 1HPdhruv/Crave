package com.srmfood.gag.feature.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.FoodCategory
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

@HiltViewModel
class ManagementMenuViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val outletRepository: OutletRepository
) : ViewModel() {

    private val _menuState = MutableStateFlow<UiState<List<FoodItem>>>(UiState.Loading)
    val menuState: StateFlow<UiState<List<FoodItem>>> = _menuState.asStateFlow()

    private val _outletsState = MutableStateFlow<List<Outlet>>(emptyList())
    val outletsState: StateFlow<List<Outlet>> = _outletsState.asStateFlow()

    private val _categoriesState = MutableStateFlow<List<FoodCategory>>(emptyList())
    val categoriesState: StateFlow<List<FoodCategory>> = _categoriesState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Filters
    private val _availabilityFilter = MutableStateFlow("ALL") // ALL, AVAILABLE, UNAVAILABLE
    val availabilityFilter: StateFlow<String> = _availabilityFilter.asStateFlow()

    private val _outletFilter = MutableStateFlow("ALL") // ALL or Outlet ID
    val outletFilter: StateFlow<String> = _outletFilter.asStateFlow()

    private val _categoryFilter = MutableStateFlow("ALL") // ALL or Category Name
    val categoryFilter: StateFlow<String> = _categoryFilter.asStateFlow()

    private val _vegFilter = MutableStateFlow("ALL") // ALL, VEG, NON_VEG
    val vegFilter: StateFlow<String> = _vegFilter.asStateFlow()

    init {
        loadFilters()
        loadMenu()
    }

    private fun loadFilters() {
        viewModelScope.launch {
            outletRepository.getAdminOutlets(null, null).onSuccess { 
                _outletsState.value = it.sortedBy { outlet -> outlet.name }
            }
            foodRepository.getCategories().onSuccess { 
                _categoriesState.value = it
            }
        }
    }

    private fun loadMenu() {
        viewModelScope.launch {
            _menuState.value = UiState.Loading
            fetchMenu()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchMenu()
            _isRefreshing.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun submitSearch() {
        loadMenu()
    }

    fun updateAvailabilityFilter(filter: String) {
        _availabilityFilter.value = filter
        loadMenu()
    }

    fun updateOutletFilter(outletId: String) {
        _outletFilter.value = outletId
        loadMenu()
    }

    fun updateCategoryFilter(categoryName: String) {
        _categoryFilter.value = categoryName
        loadMenu()
    }

    fun updateVegFilter(vegFilter: String) {
        _vegFilter.value = vegFilter
        loadMenu()
    }

    private suspend fun fetchMenu() {
        val q = _searchQuery.value.takeIf { it.isNotBlank() }
        val outlet = if (_outletFilter.value == "ALL") null else _outletFilter.value
        val category = if (_categoryFilter.value == "ALL") null else _categoryFilter.value
        val availability = if (_availabilityFilter.value == "ALL") null else _availabilityFilter.value
        val isVeg = when (_vegFilter.value) {
            "VEG" -> true
            "NON_VEG" -> false
            else -> null
        }

        foodRepository.getAdminFoodItems(
            searchQuery = q,
            outletId = outlet,
            category = category,
            availability = availability,
            isVeg = isVeg
        ).fold(
            onSuccess = { _menuState.value = UiState.Success(it) },
            onFailure = { _menuState.value = UiState.Error("Management data is temporarily unavailable. Please try again.") }
        )
    }
}
