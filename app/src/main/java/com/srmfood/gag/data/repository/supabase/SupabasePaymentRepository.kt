package com.srmfood.gag.data.repository.supabase

import com.srmfood.gag.data.remote.dto.PaymentRecordDto
import com.srmfood.gag.domain.model.PaymentRecord
import com.srmfood.gag.domain.model.PaymentVerificationRequest
import com.srmfood.gag.domain.model.RazorpayOrderDetails
import com.srmfood.gag.domain.repository.PaymentRepository
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class CreateOrderRequest(val order_id: String)

@Singleton
class SupabasePaymentRepository @Inject constructor(
    private val functions: Functions,
    private val postgrest: Postgrest
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

    override suspend fun getAdminPayments(
        statusFilter: String?,
        providerFilter: String?,
        searchQuery: String?
    ): Result<List<PaymentRecord>> = runCatching {
        val dtos = postgrest["payments"].select {
            filter {
                if (!statusFilter.isNullOrBlank() && statusFilter != "ALL") {
                    eq("status", statusFilter)
                }
                if (!providerFilter.isNullOrBlank() && providerFilter != "ALL") {
                    eq("gateway_provider", providerFilter)
                }
                if (!searchQuery.isNullOrBlank()) {
                    val q = searchQuery.trim()
                    or {
                        ilike("order_id", "%$q%")
                        ilike("razorpay_order_id", "%$q%")
                        ilike("razorpay_payment_id", "%$q%")
                    }
                }
            }
        }.decodeList<PaymentRecordDto>()
        
        dtos.map { dto ->
            PaymentRecord(
                id = dto.id,
                orderId = dto.orderId,
                razorpayPaymentId = dto.razorpayPaymentId,
                razorpayOrderId = dto.razorpayOrderId,
                amount = dto.amount,
                currency = dto.currency,
                status = dto.status,
                gatewayProvider = dto.gatewayProvider,
                createdAt = dto.createdAt,
                updatedAt = dto.updatedAt
            )
        }
    }

    override suspend fun getPaymentById(id: String): Result<PaymentRecord> = runCatching {
        val dto = postgrest["payments"].select {
            filter { eq("id", id) }
        }.decodeSingle<PaymentRecordDto>()
        
        PaymentRecord(
            id = dto.id,
            orderId = dto.orderId,
            razorpayPaymentId = dto.razorpayPaymentId,
            razorpayOrderId = dto.razorpayOrderId,
            amount = dto.amount,
            currency = dto.currency,
            status = dto.status,
            gatewayProvider = dto.gatewayProvider,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt
        )
    }
}
