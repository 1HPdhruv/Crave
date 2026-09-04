package com.srmfood.gag.core.payment

import com.razorpay.PaymentData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class RazorpayResult {
    data class Success(val data: PaymentData) : RazorpayResult()
    data class Error(val code: Int, val message: String?, val data: PaymentData?) : RazorpayResult()
}

@Singleton
class RazorpayManager @Inject constructor() {
    private val _results = MutableSharedFlow<RazorpayResult>()
    val results = _results.asSharedFlow()

    suspend fun onPaymentSuccess(paymentId: String?, data: PaymentData?) {
        data?.let { _results.emit(RazorpayResult.Success(it)) }
    }

    suspend fun onPaymentError(code: Int, message: String?, data: PaymentData?) {
        _results.emit(RazorpayResult.Error(code, message, data))
    }
}
