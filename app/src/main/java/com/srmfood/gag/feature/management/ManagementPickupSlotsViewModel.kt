package com.srmfood.gag.feature.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.PickupSlot
import com.srmfood.gag.domain.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@HiltViewModel
class ManagementPickupSlotsViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    
    private val _selectedDate = MutableStateFlow(getCurrentDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _availableDates = MutableStateFlow(generateAvailableDates())
    val availableDates: StateFlow<List<String>> = _availableDates.asStateFlow()

    private val _allItems = MutableStateFlow<List<PickupSlot>>(emptyList())
    private val _uiState = MutableStateFlow<UiState<List<PickupSlot>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<PickupSlot>>> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Outlet Filter
    private val _selectedOutlet = MutableStateFlow<String?>(null)
    val selectedOutlet: StateFlow<String?> = _selectedOutlet.asStateFlow()

    val availableOutlets: StateFlow<List<String>> = _allItems.map { items ->
        items.map { it.outletName }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadSlots()

        viewModelScope.launch {
            combine(_allItems, _selectedOutlet) { items, outlet ->
                if (outlet == null || outlet == "All Outlets") items else items.filter { it.outletName == outlet }
            }.collect { filtered ->
                if (_uiState.value is UiState.Success || filtered.isNotEmpty()) {
                    _uiState.value = UiState.Success(filtered)
                }
            }
        }
    }

    private fun loadSlots() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            fetchSlots(_selectedDate.value)
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchSlots(_selectedDate.value)
            _isRefreshing.value = false
        }
    }

    fun selectDate(dateStr: String) {
        if (_selectedDate.value == dateStr) return
        _selectedDate.value = dateStr
        _selectedOutlet.value = null // reset filter
        loadSlots()
    }

    fun setOutletFilter(outletName: String?) {
        _selectedOutlet.value = outletName
    }

    private suspend fun fetchSlots(date: String) {
        orderRepository.getAdminPickupSlots(date).fold(
            onSuccess = { items ->
                // Sort by start time then outlet name
                val sorted = items.sortedWith(compareBy({ it.startTime }, { it.outletName }))
                _allItems.value = sorted
                if (_uiState.value is UiState.Loading) { // Initial load
                    _uiState.value = UiState.Success(sorted)
                }
            },
            onFailure = { error ->
                _uiState.value = UiState.Error("Management data is temporarily unavailable. Please try again.")
            }
        )
    }

    private fun getCurrentDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = timeZone
        return sdf.format(Calendar.getInstance(timeZone).time)
    }

    private fun generateAvailableDates(): List<String> {
        val dates = mutableListOf<String>()
        val cal = Calendar.getInstance(timeZone)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = timeZone
        
        // Show today + next 6 days (1 week window)
        for (i in 0..6) {
            dates.add(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return dates
    }
}
