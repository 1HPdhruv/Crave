package com.srmfood.gag.feature.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.repository.AdminRepository
import com.srmfood.gag.domain.usecase.admin.ManagementAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManagementAnalyticsViewModel @Inject constructor(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _analyticsState = MutableStateFlow<UiState<ManagementAnalytics>>(UiState.Loading)
    val analyticsState: StateFlow<UiState<ManagementAnalytics>> = _analyticsState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadAnalytics()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            _analyticsState.value = UiState.Loading
            fetchAnalytics()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchAnalytics()
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchAnalytics() {
        adminRepository.getManagementAnalytics().fold(
            onSuccess = { data ->
                _analyticsState.value = UiState.Success(data)
            },
            onFailure = { error ->
                _analyticsState.value = UiState.Error("Management data is temporarily unavailable. Please try again.")
            }
        )
    }
}
