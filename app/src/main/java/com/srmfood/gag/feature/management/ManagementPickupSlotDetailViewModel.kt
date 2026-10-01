package com.srmfood.gag.feature.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.PickupSlot
import com.srmfood.gag.domain.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@HiltViewModel
class ManagementPickupSlotDetailViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val timeZone = TimeZone.getTimeZone("Asia/Kolkata")

    private val _uiState = MutableStateFlow<UiState<PickupSlot>>(UiState.Loading)
    val uiState: StateFlow<UiState<PickupSlot>> = _uiState.asStateFlow()

    fun loadSlotDetail(slotId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            // To find the slot efficiently without a dedicated getPickupSlotById method, 
            // we generate the active date range and fetch those slots.
            val dates = generateAvailableDates()
            var foundSlot: PickupSlot? = null
            
            for (date in dates) {
                val result = orderRepository.getAdminPickupSlots(date)
                if (result.isSuccess) {
                    foundSlot = result.getOrNull()?.find { it.id == slotId }
                    if (foundSlot != null) break
                }
            }
            
            if (foundSlot != null) {
                _uiState.value = UiState.Success(foundSlot)
            } else {
                _uiState.value = UiState.Error("Pickup slot not found")
            }
        }
    }

    private fun generateAvailableDates(): List<String> {
        val dates = mutableListOf<String>()
        val cal = Calendar.getInstance(timeZone)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = timeZone
        
        // Check today + next 6 days (1 week window)
        for (i in 0..6) {
            dates.add(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return dates
    }
}
