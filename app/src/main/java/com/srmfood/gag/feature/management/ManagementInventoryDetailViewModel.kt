package com.srmfood.gag.feature.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.repository.AdminRepository
import com.srmfood.gag.domain.usecase.admin.ManagementInventoryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManagementInventoryDetailViewModel @Inject constructor(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ManagementInventoryItem>>(UiState.Loading)
    val uiState: StateFlow<UiState<ManagementInventoryItem>> = _uiState.asStateFlow()

    fun loadInventoryDetail(inventoryId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            adminRepository.getInventory().fold(
                onSuccess = { items ->
                    val item = items.find { it.id == inventoryId }
                    if (item != null) {
                        _uiState.value = UiState.Success(item)
                    } else {
                        _uiState.value = UiState.Error("Inventory item not found")
                    }
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error("Management data is temporarily unavailable. Please try again.")
                }
            )
        }
    }
}
