package com.srmfood.gag.feature.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.PaymentRecord
import com.srmfood.gag.domain.repository.PaymentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManagementPaymentsData(
    val payments: List<PaymentRecord>,
    val totalRevenue: Double,
    val onlineRevenue: Double,
    val cashRevenue: Double,
    val refundedAmount: Double
)

@HiltViewModel
class ManagementPaymentsViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val _paymentsState = MutableStateFlow<UiState<ManagementPaymentsData>>(UiState.Loading)
    val paymentsState: StateFlow<UiState<ManagementPaymentsData>> = _paymentsState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow("ALL")
    val statusFilter: StateFlow<String> = _statusFilter.asStateFlow()

    private val _providerFilter = MutableStateFlow("ALL")
    val providerFilter: StateFlow<String> = _providerFilter.asStateFlow()

    init {
        loadPayments()
    }

    private fun loadPayments() {
        viewModelScope.launch {
            _paymentsState.value = UiState.Loading
            fetchPayments()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchPayments()
            _isRefreshing.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun submitSearch() {
        loadPayments()
    }

    fun updateStatusFilter(status: String) {
        _statusFilter.value = status
        loadPayments()
    }

    fun updateProviderFilter(provider: String) {
        _providerFilter.value = provider
        loadPayments()
    }

    private suspend fun fetchPayments() {
        val q = _searchQuery.value.takeIf { it.isNotBlank() }
        val status = if (_statusFilter.value == "ALL") null else _statusFilter.value
        val provider = if (_providerFilter.value == "ALL") null else _providerFilter.value

        paymentRepository.getAdminPayments(
            statusFilter = status,
            providerFilter = provider,
            searchQuery = q
        ).fold(
            onSuccess = { payments ->
                // Calculate quick metrics
                // We only sum PAID or CAPTURED for revenue
                val revenuePayments = payments.filter { it.status == "PAID" || it.status == "CAPTURED" }
                val totalRevenue = revenuePayments.sumOf { it.amount }
                val onlineRevenue = revenuePayments.filter { it.gatewayProvider == "RAZORPAY" }.sumOf { it.amount }
                val cashRevenue = revenuePayments.filter { it.gatewayProvider == "CASH" }.sumOf { it.amount }
                
                val refundedAmount = payments.filter { it.status == "REFUNDED" }.sumOf { it.amount }

                _paymentsState.value = UiState.Success(
                    ManagementPaymentsData(
                        payments = payments.sortedByDescending { it.createdAt }, // Newest first
                        totalRevenue = totalRevenue,
                        onlineRevenue = onlineRevenue,
                        cashRevenue = cashRevenue,
                        refundedAmount = refundedAmount
                    )
                )
            },
            onFailure = {
                _paymentsState.value = UiState.Error("Management data is temporarily unavailable. Please try again.")
            }
        )
    }
}
