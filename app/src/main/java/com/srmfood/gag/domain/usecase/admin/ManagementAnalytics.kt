package com.srmfood.gag.domain.usecase.admin

import kotlinx.serialization.Serializable

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
    val availableFoodItems: Int
)
