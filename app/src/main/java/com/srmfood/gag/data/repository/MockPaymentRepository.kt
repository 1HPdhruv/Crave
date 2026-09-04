package com.srmfood.gag.data.repository

import com.srmfood.gag.domain.model.PaymentVerificationRequest
import com.srmfood.gag.domain.model.RazorpayOrderDetails
import com.srmfood.gag.domain.repository.PaymentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockPaymentRepository @Inject constructor() : PaymentRepository {
    override suspend fun createRazorpayOrder(orderId: String): Result<RazorpayOrderDetails> {
        return Result.success(RazorpayOrderDetails(
            razorpay_order_id = "rzp_test_mock_order_id",
            amount = 100,
            key_id = "rzp_test_mock_key"
        ))
    }

    override suspend fun verifyRazorpayPayment(request: PaymentVerificationRequest): Result<Unit> {
        return Result.success(Unit)
    }
}
