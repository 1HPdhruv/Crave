package com.srmfood.gag.domain.repository

import com.srmfood.gag.domain.model.PaymentVerificationRequest
import com.srmfood.gag.domain.model.RazorpayOrderDetails

interface PaymentRepository {
    suspend fun createRazorpayOrder(orderId: String): Result<RazorpayOrderDetails>
    suspend fun verifyRazorpayPayment(request: PaymentVerificationRequest): Result<Unit>

    // Management
    suspend fun getAdminPayments(
        statusFilter: String? = null,
        providerFilter: String? = null,
        searchQuery: String? = null
    ): Result<List<com.srmfood.gag.domain.model.PaymentRecord>>

    suspend fun getPaymentById(id: String): Result<com.srmfood.gag.domain.model.PaymentRecord>
}
