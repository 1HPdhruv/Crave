package com.srmfood.gag.feature.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.usecase.admin.SystemStats
import com.srmfood.gag.domain.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManagementViewModel @Inject constructor(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _stats = MutableStateFlow<UiState<SystemStats>>(UiState.Loading)
    val stats: StateFlow<UiState<SystemStats>> = _stats.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadOverview()
    }

    private fun loadOverview() {
        viewModelScope.launch {
            _stats.value = UiState.Loading
            fetchStats()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchStats()
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchStats() {
        adminRepository.getManagementAnalytics().fold(
            onSuccess = { analytics -> 
                _stats.value = UiState.Success(
                    SystemStats(
                        totalUsers = analytics.totalStudents,
                        totalVendors = analytics.totalVendors,
                        totalOutlets = analytics.totalOutlets,
                        totalOrders = analytics.totalOrders,
                        activeOrders = analytics.activeOrders,
                        completedOrders = analytics.completedOrders,
                        revenue = analytics.paidRevenue,
                        ordersToday = analytics.ordersToday,
                        revenueToday = analytics.revenueToday
                    )
                )
            },
            onFailure = { _stats.value = UiState.Error("Management data is temporarily unavailable. Please try again.") }
        )
    }
}
