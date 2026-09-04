package com.srmfood.gag.domain.repository

import com.srmfood.gag.domain.model.PaymentVerificationRequest
import com.srmfood.gag.domain.model.RazorpayOrderDetails

interface PaymentRepository {
    suspend fun createRazorpayOrder(orderId: String): Result<RazorpayOrderDetails>
    suspend fun verifyRazorpayPayment(request: PaymentVerificationRequest): Result<Unit>
}
