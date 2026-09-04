package com.srmfood.gag.data.remote.api

import com.srmfood.gag.data.remote.dto.CancelOrderRequestDto
import com.srmfood.gag.data.remote.dto.ConfirmPickupRequestDto
import com.srmfood.gag.data.remote.dto.OrderDto
import com.srmfood.gag.data.remote.dto.PickupSlotDto
import com.srmfood.gag.data.remote.dto.PlaceOrderRequestDto
import com.srmfood.gag.data.remote.dto.QrTokenResponseDto
import com.srmfood.gag.data.remote.dto.RejectOrderRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface OrderApi {
    @GET("orders")
    suspend fun getOrders(): List<OrderDto>

    @GET("orders/{id}")
    suspend fun getOrderById(@Path("id") id: String): OrderDto

    @POST("orders")
    suspend fun placeOrder(@Body request: PlaceOrderRequestDto): OrderDto

    @PATCH("orders/{id}/cancel")
    suspend fun cancelOrder(
        @Path("id") id: String,
        @Body request: CancelOrderRequestDto
    ): OrderDto

    @GET("orders/{id}/qr-token")
    suspend fun getQrToken(@Path("id") id: String): QrTokenResponseDto

    @GET("outlets/{outletId}/slots")
    suspend fun getPickupSlots(
        @Path("outletId") outletId: String,
        @Query("date") date: String
    ): List<PickupSlotDto>
}

interface VendorApi {
    @GET("vendor/orders")
    suspend fun getVendorOrders(@Query("status") status: String? = null): List<OrderDto>

    @GET("vendor/orders/{id}")
    suspend fun getVendorOrderById(@Path("id") id: String): OrderDto

    @PATCH("vendor/orders/{id}/accept")
    suspend fun acceptOrder(@Path("id") id: String): OrderDto

    @PATCH("vendor/orders/{id}/reject")
    suspend fun rejectOrder(
        @Path("id") id: String,
        @Body request: RejectOrderRequestDto
    ): OrderDto

    @PATCH("vendor/orders/{id}/preparing")
    suspend fun startPreparing(@Path("id") id: String): OrderDto

    @PATCH("vendor/orders/{id}/ready")
    suspend fun markReady(@Path("id") id: String): OrderDto

    @POST("vendor/orders/pickup")
    suspend fun confirmPickup(@Body request: ConfirmPickupRequestDto): OrderDto

    @GET("vendor/menu")
    suspend fun getVendorMenu(): List<com.srmfood.gag.data.remote.dto.FoodItemDto>
}

interface AdminApi {
    @GET("admin/users")
    suspend fun getUsers(@Query("page") page: Int = 1, @Query("limit") limit: Int = 20): List<com.srmfood.gag.data.remote.dto.UserDto>

    @GET("admin/orders")
    suspend fun getAdminOrders(@Query("status") status: String? = null): List<OrderDto>

    @GET("admin/outlets")
    suspend fun getAdminOutlets(): List<com.srmfood.gag.data.remote.dto.OutletDto>

    @GET("admin/analytics/summary")
    suspend fun getAnalyticsSummary(): Map<String, Any>
}

interface NotificationApi {
    @GET("notifications")
    suspend fun getNotifications(): List<Map<String, Any>>

    @PATCH("notifications/{id}/read")
    suspend fun markAsRead(@Path("id") id: String)
}
