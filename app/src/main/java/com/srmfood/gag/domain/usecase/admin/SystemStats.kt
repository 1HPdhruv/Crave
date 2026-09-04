package com.srmfood.gag.domain.usecase.admin

import kotlinx.serialization.Serializable

@Serializable
data class SystemStats(
    val totalUsers: Int,
    val totalVendors: Int,
    val totalOutlets: Int,
    val totalOrders: Int,
    val activeOrders: Int,
    val completedOrders: Int,
    val revenue: Double,
    val ordersToday: Int,
    val revenueToday: Double
)
