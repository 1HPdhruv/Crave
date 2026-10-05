package com.srmfood.gag.domain.usecase.admin

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class TrendPoint(
    @SerialName("label") val label: String = "",
    @SerialName("orders") val orders: Int = 0,
    @SerialName("revenue") val revenue: Double = 0.0
)

@Serializable
data class ManagementAnalytics(
    val ordersToday: Int,
    val revenueToday: Double,
    val ordersThisWeek: Int,
    val revenueThisWeek: Double,
    val ordersThisMonth: Int,
    val revenueThisMonth: Double,

    val totalOrders: Int,
    val completedOrders: Int,
    val cancelledOrders: Int,
    val rejectedOrders: Int,
    val activeOrders: Int,

    val paidRevenue: Double,
    val refundedAmount: Double,
    val cashRevenue: Double,
    val razorpayRevenue: Double,

    val totalStudents: Int,
    val totalVendors: Int,
    val totalOutlets: Int,
    val activeOutlets: Int,

    val totalFoodItems: Int,
    val availableFoodItems: Int,

    val trendToday: List<TrendPoint> = emptyList(),
    val trendWeek: List<TrendPoint> = emptyList(),
    val trendMonth: List<TrendPoint> = emptyList()
)
