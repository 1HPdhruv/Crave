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
import kotlinx.coroutines.flow.update
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

data class OutletOrderingUiState(
    val pending: com.srmfood.gag.domain.model.OutletOrderingAction? = null,
    val reason: String = "",
    val confirmName: String = "",
    val isBusy: Boolean = false,
    val error: String? = null,
    val history: List<com.srmfood.gag.domain.model.OutletManagementActionRecord> = emptyList()
)

@HiltViewModel
class ManagementOutletDetailViewModel @Inject constructor(
    private val outletRepository: OutletRepository,
    private val vendorRepository: ManagementVendorRepository,
    private val foodRepository: FoodRepository,
    private val orderRepository: OrderRepository,
    private val orderingRepository: com.srmfood.gag.domain.repository.OutletOrderingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val outletId: String = checkNotNull(savedStateHandle["outletId"])

    private val _detailState = MutableStateFlow<UiState<OutletDetailData>>(UiState.Loading)
    val detailState: StateFlow<UiState<OutletDetailData>> = _detailState.asStateFlow()

    private val _ordering = MutableStateFlow(OutletOrderingUiState())
    val ordering: StateFlow<OutletOrderingUiState> = _ordering.asStateFlow()

    init {
        loadData()
    }

    fun startAction(a: com.srmfood.gag.domain.model.OutletOrderingAction) =
        _ordering.update { it.copy(pending = a, reason = "", confirmName = "", error = null) }
    fun cancelAction() = _ordering.update { it.copy(pending = null, reason = "", confirmName = "") }
    fun setReason(v: String) = _ordering.update { it.copy(reason = v.take(2000)) }
    fun setConfirmName(v: String) = _ordering.update { it.copy(confirmName = v) }
    fun dismissOrderingError() = _ordering.update { it.copy(error = null) }

    fun submitAction() {
        val s = _ordering.value
        val action = s.pending ?: return
        if (s.isBusy) return // one write at a time
        _ordering.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            orderingRepository.setOrderingStatus(outletId, action, s.reason, s.confirmName.takeIf { it.isNotBlank() }).fold(
                onSuccess = { _ordering.update { it.copy(isBusy = false, pending = null, reason = "", confirmName = "") }; loadData() },
                onFailure = { e ->
                    _ordering.update { it.copy(isBusy = false, pending = null, error = outletActionErrorMessage(e)) }
                    loadData() // state may have changed under us (e.g. another admin acted): show the truth
                }
            )
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            orderingRepository.getActionHistory(outletId).onSuccess { h -> _ordering.update { it.copy(history = h) } }
        }
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _detailState.value = UiState.Loading
            loadHistory()
            val outletResult = outletRepository.fetchOutlet(outletId)
            
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
