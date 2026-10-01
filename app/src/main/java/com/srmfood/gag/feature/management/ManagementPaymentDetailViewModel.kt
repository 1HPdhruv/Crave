package com.srmfood.gag.feature.management

import androidx.lifecycle.SavedStateHandle
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

@HiltViewModel
class ManagementPaymentDetailViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val paymentId: String = checkNotNull(savedStateHandle["paymentId"]) { "paymentId must be provided" }

    private val _paymentState = MutableStateFlow<UiState<PaymentRecord>>(UiState.Loading)
    val paymentState: StateFlow<UiState<PaymentRecord>> = _paymentState.asStateFlow()

    init {
        loadPayment()
    }

    fun refresh() {
        loadPayment()
    }

    private fun loadPayment() {
        viewModelScope.launch {
            _paymentState.value = UiState.Loading
            paymentRepository.getPaymentById(paymentId).fold(
                onSuccess = { _paymentState.value = UiState.Success(it) },
                onFailure = { _paymentState.value = UiState.Error("Management data is temporarily unavailable. Please try again.") }
            )
        }
    }
}
