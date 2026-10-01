package com.srmfood.gag.feature.management

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.model.Outlet
import com.srmfood.gag.domain.model.PickupSlot
import com.srmfood.gag.domain.model.admin.VendorProfile
import com.srmfood.gag.domain.repository.FoodRepository
import com.srmfood.gag.domain.repository.ManagementVendorRepository
import com.srmfood.gag.domain.repository.OrderRepository
import com.srmfood.gag.domain.repository.OutletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class OutletDetailData(
    val outlet: Outlet,
    val vendor: VendorProfile?,
    val foodItems: List<FoodItem>?,
    val todayPickupSlots: List<PickupSlot>?
)

@HiltViewModel
class ManagementOutletDetailViewModel @Inject constructor(
    private val outletRepository: OutletRepository,
    private val vendorRepository: ManagementVendorRepository,
    private val foodRepository: FoodRepository,
    private val orderRepository: OrderRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val outletId: String = checkNotNull(savedStateHandle["outletId"])

    private val _detailState = MutableStateFlow<UiState<OutletDetailData>>(UiState.Loading)
    val detailState: StateFlow<UiState<OutletDetailData>> = _detailState.asStateFlow()

    init {
        loadData()
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _detailState.value = UiState.Loading
            
            val outletResult = outletRepository.getOutletById(outletId)
            
            outletResult.fold(
                onSuccess = { outlet ->
                    // Try to fetch auxiliary data, but don't fail if they do
                    val vendor = vendorRepository.getVendorById(outlet.vendorId).getOrNull()
                    val foodItems = foodRepository.getMenuByOutlet(outlet.id).getOrNull()
                    
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val pickupSlots = orderRepository.getPickupSlots(outlet.id, today).getOrNull()
                    
                    _detailState.value = UiState.Success(
                        OutletDetailData(
                            outlet = outlet,
                            vendor = vendor,
                            foodItems = foodItems,
                            todayPickupSlots = pickupSlots
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
