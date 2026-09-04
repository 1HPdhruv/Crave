package com.srmfood.gag.data.repository.supabase

import com.srmfood.gag.domain.model.PaymentVerificationRequest
import com.srmfood.gag.domain.model.RazorpayOrderDetails
import com.srmfood.gag.domain.repository.PaymentRepository
import io.github.jan.supabase.functions.Functions
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class CreateOrderRequest(val order_id: String)

@Singleton
class SupabasePaymentRepository @Inject constructor(
    private val functions: Functions
) : PaymentRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createRazorpayOrder(orderId: String): Result<RazorpayOrderDetails> = runCatching {
        val response = functions.invoke("create-razorpay-order", CreateOrderRequest(orderId))
        json.decodeFromString<RazorpayOrderDetails>(response.bodyAsText())
    }

    override suspend fun verifyRazorpayPayment(request: PaymentVerificationRequest): Result<Unit> = runCatching {
        functions.invoke("verify-razorpay-payment", request)
        Unit
    }
}
