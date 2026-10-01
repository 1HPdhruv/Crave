package com.srmfood.gag.feature.management

import androidx.lifecycle.SavedStateHandle
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
class ManagementVendorDetailViewModel @Inject constructor(
    private val vendorRepository: ManagementVendorRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val vendorId: String = checkNotNull(savedStateHandle["vendorId"])

    private val _vendorState = MutableStateFlow<UiState<VendorProfile>>(UiState.Loading)
    val vendorState: StateFlow<UiState<VendorProfile>> = _vendorState.asStateFlow()

    init {
        loadVendor()
    }

    fun refresh() {
        loadVendor()
    }

    private fun loadVendor() {
        viewModelScope.launch {
            _vendorState.value = UiState.Loading
            vendorRepository.getVendorById(vendorId).fold(
                onSuccess = { _vendorState.value = UiState.Success(it) },
                onFailure = { _vendorState.value = UiState.Error("Management data is temporarily unavailable. Please try again.") }
            )
        }
    }
}
