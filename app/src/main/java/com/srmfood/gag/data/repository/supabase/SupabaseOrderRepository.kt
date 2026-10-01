package com.srmfood.gag.data.repository.supabase

import com.srmfood.gag.data.local.dao.CartDao
import com.srmfood.gag.data.local.dao.OrderDao
import com.srmfood.gag.data.mapper.toDomain
import com.srmfood.gag.data.mapper.toEntity
import com.srmfood.gag.data.remote.dto.OrderDto
import com.srmfood.gag.data.remote.dto.OrderItemDto
import com.srmfood.gag.data.remote.dto.PickupSlotDto
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.OrderStatus
import com.srmfood.gag.domain.model.PaymentMethod
import com.srmfood.gag.domain.model.PickupSlot
import com.srmfood.gag.domain.repository.OrderRepository
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch

// ─── RPC Request / Response DTOs ─────────────────────────────────────────────

/**
 * Parameters for the place_order() Postgres RPC.
 * The server calculates prices, deducts inventory, reserves the slot,
 * and generates the QR token atomically. Never trust the client for any of these.
 */
@Serializable
data class PlaceOrderRpcParams(
    @SerialName("p_cart_id")        val cartId: String,
    @SerialName("p_pickup_slot_id") val pickupSlotId: String,
    @SerialName("p_payment_method") val paymentMethod: String = "PAY_AT_COUNTER"
)

/**
 * Parameters for the verify_pickup_token() Postgres RPC.
 * Called by the vendor app when scanning a student's QR code.
 */
@Serializable
data class VerifyPickupTokenParams(
    @SerialName("p_token") val token: String
)

/** Response from verify_pickup_token() RPC. */
@Serializable
data class VerifyPickupTokenResult(
    val success: Boolean,
    @SerialName("order_id")     val orderId: String,
    @SerialName("order_number") val orderNumber: String,
    @SerialName("picked_up_at") val pickedUpAt: String
)

/** Row from pickup_tokens table (used to fetch the student's QR token value). */
@Serializable
data class PickupTokenResponse(
    @SerialName("token_value") val tokenValue: String,
    @SerialName("expires_at")  val expiresAt: String,
    @SerialName("is_used")     val isUsed: Boolean
)

// ─── Vendor Action RPC DTOs ──────────────────────────────────────────────────
@Serializable
data class OrderIdRpcParams(@SerialName("p_order_id") val orderId: String)

@Serializable
data class RejectOrderRpcParams(
    @SerialName("p_order_id") val orderId: String,
    @SerialName("p_reason") val reason: String
)

@Singleton
class SupabaseOrderRepository @Inject constructor(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val client: io.github.jan.supabase.SupabaseClient,
    private val orderDao: OrderDao,
    private val cartDao: CartDao
) : OrderRepository {

    override fun getOrders(): Flow<List<Order>> {
        GlobalScope.launch {
            try {
                val session = auth.currentSessionOrNull()
                val userId = session?.user?.id
                if (userId != null) {
                    val dtos = postgrest["orders"].select(Columns.raw("*, pickup_slots(*), items:order_items(*, order_item_customizations(*))")) {
                        filter { eq("user_id", userId) }
                    }.decodeList<OrderDto>()
                    orderDao.insertAll(dtos.map { it.toEntity() })
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return orderDao.observeOrders().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAdminOrders(statusFilter: String?, searchQuery: String?): Result<List<Order>> = runCatching {
        val dtos = postgrest["orders"].select(Columns.raw("*, pickup_slots(*), items:order_items(*, order_item_customizations(*))")) {
            filter {
                if (statusFilter != null) {
                    when (statusFilter.uppercase()) {
                        "ACTIVE" -> isIn("status", listOf("PLACED", "ACCEPTED", "PREPARING", "READY"))
                        "COMPLETED" -> eq("status", "PICKED_UP")
                        "CANCELLED" -> isIn("status", listOf("CANCELLED", "REJECTED", "REFUNDED"))
                        else -> eq("status", statusFilter.uppercase())
                    }
                }
                if (!searchQuery.isNullOrBlank()) {
                    // Search by order_number (e.g. #CRV-10283 -> drop # if present)
                    val q = searchQuery.replace("#", "").trim()
                    // Supabase PostgREST allows basic OR logic if needed, but for simplicity we'll just ilike order_number or outlet_name
                    or {
                        ilike("order_number", "%$q%")
                        ilike("outlet_name", "%$q%")
                    }
                }
            }
            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            limit(100) // Simple initial limit as requested
        }.decodeList<OrderDto>()
        
        dtos.map { it.toDomain() }
    }

    override suspend fun getOrderById(orderId: String): Result<Order> = runCatching {
        val dto = postgrest["orders"].select(Columns.raw("*, pickup_slots(*), items:order_items(*, order_item_customizations(*))")) {
            filter { eq("id", orderId) }
        }.decodeSingle<OrderDto>()
        
        // Caching locally is handled via sync mechanism or here
        dto.toDomain()
    }

    override suspend fun placeOrder(
        cartId: String,
        outletId: String,
        pickupSlotId: String,
        paymentMethod: PaymentMethod,
        specialInstructions: String?
    ): Result<Order> = runCatching {
        val session = auth.currentSessionOrNull() ?: throw Exception("User not logged in")
        val userId = session.user?.id ?: throw Exception("Invalid user")

        // 2. Call the place_order() RPC — all validation and price calculation is server-side
        val orderId = postgrest.rpc(
            function = "place_order",
            parameters = PlaceOrderRpcParams(
                cartId        = cartId,
                pickupSlotId  = pickupSlotId,
                paymentMethod = paymentMethod.name
            )
        ).decodeAs<String>()

        // 3. Clear local cart only if paying at counter (Online clears after success)
        if (paymentMethod == PaymentMethod.PAY_AT_COUNTER) {
            cartDao.clearCart()
        }

        // 4. Return the full order
        getOrderById(orderId).getOrThrow()
    }

    override suspend fun cancelOrder(orderId: String, reason: String): Result<Order> = runCatching {
        postgrest["orders"].update(
            {
                set("status", "CANCELLED")
                set("cancellation_reason", reason)
            }
        ) {
            filter { eq("id", orderId) }
        }
        orderDao.updateStatus(orderId, "CANCELLED")
        getOrderById(orderId).getOrThrow()
    }

    override suspend fun getPickupSlots(outletId: String, date: String): Result<List<PickupSlot>> = runCatching {
        val dtos = postgrest["pickup_slots"].select {
            filter { 
                eq("outlet_id", outletId)
                eq("slot_date", date)
            }
        }.decodeList<PickupSlotDto>()
        
        dtos.map { it.toDomain() }
    }.onFailure { e ->
        android.util.Log.e("PickupSlotDebug", "Failed to fetch pickup slots for outlet $outletId", e)
    }

    override suspend fun getAdminPickupSlots(date: String): Result<List<PickupSlot>> = runCatching {
        if (auth.currentSessionOrNull() == null) throw Exception("User not logged in")
        val dtos = postgrest["pickup_slots"].select(Columns.raw("*, outlets(name)")) {
            filter { 
                eq("slot_date", date)
            }
        }.decodeList<PickupSlotDto>()
        
        dtos.map { it.toDomain() }
    }


    override suspend fun getQrToken(orderId: String): Result<String> = runCatching {
        // Fetch the real token generated by place_order() RPC from pickup_tokens table.
        val tokenRow = postgrest["pickup_tokens"]
            .select(Columns.raw("token_value, expires_at, is_used")) {
                filter { eq("order_id", orderId) }
            }
            .decodeSingle<PickupTokenResponse>()

        if (tokenRow.isUsed) throw Exception("Pickup token has already been used.")
        tokenRow.tokenValue
    }

    override fun observeOrderStatus(orderId: String): Flow<OrderStatus> = kotlinx.coroutines.flow.callbackFlow {
        val local = orderDao.getOrderById(orderId)
        local?.let { trySend(OrderStatus.valueOf(it.status)) }

        val channel = client.realtime.channel("public:orders")
        val changes = channel.postgresChangeFlow<PostgresAction.Update>("public") {
            table = "orders"
            filter = "id=eq.$orderId"
        }

        val job = launch {
            changes.collect { action ->
                val statusString = action.record["status"]?.toString()?.replace("\"", "")
                if (statusString != null) {
                    val newStatus = OrderStatus.valueOf(statusString)
                    orderDao.updateStatus(orderId, newStatus.name)
                    trySend(newStatus)
                }
            }
        }

        channel.subscribe()
        awaitClose { 
            GlobalScope.launch { channel.unsubscribe() }
            job.cancel()
        }
    }

    // ─── Vendor Operations ────────────────────────────────────────────────────────
    
    override suspend fun getVendorOrders(status: OrderStatus?): Result<List<Order>> = runCatching {
        val session = auth.currentSessionOrNull() ?: throw Exception("User not logged in")
        val vendorId = session.user?.id ?: throw Exception("Invalid user")

        val dtos = postgrest["orders"].select(Columns.raw("*, pickup_slots(*), items:order_items(*, order_item_customizations(*))")) {
            filter {
                eq("vendor_id", vendorId)
                if (status != null) {
                    eq("status", status.name)
                }
            }
        }.decodeList<OrderDto>()
        
        dtos.map { it.toDomain() }.sortedByDescending { it.createdAt }
    }

    override suspend fun acceptOrder(orderId: String): Result<Order> = runCatching {
        postgrest.rpc("vendor_accept_order", OrderIdRpcParams(orderId))
        getOrderById(orderId).getOrThrow()
    }

    override suspend fun rejectOrder(orderId: String, reason: String): Result<Order> = runCatching {
        postgrest.rpc("vendor_reject_order", RejectOrderRpcParams(orderId, reason))
        getOrderById(orderId).getOrThrow()
    }

    override suspend fun startPreparing(orderId: String): Result<Order> = runCatching {
        postgrest.rpc("vendor_start_preparing", OrderIdRpcParams(orderId))
        getOrderById(orderId).getOrThrow()
    }

    override suspend fun markReady(orderId: String): Result<Order> = runCatching {
        postgrest.rpc("vendor_mark_ready", OrderIdRpcParams(orderId))
        getOrderById(orderId).getOrThrow()
    }

    override suspend fun confirmPickup(qrToken: String): Result<Order> = runCatching {
        val result = postgrest.rpc(
            function = "verify_pickup_token",
            parameters = VerifyPickupTokenParams(token = qrToken)
        ).decodeAs<VerifyPickupTokenResult>()

        if (!result.success) throw Exception("Pickup verification failed")
        getOrderById(result.orderId).getOrThrow()
    }
}
