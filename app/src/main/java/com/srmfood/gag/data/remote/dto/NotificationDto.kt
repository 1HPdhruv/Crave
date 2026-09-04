package com.srmfood.gag.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("title") val title: String,
    @SerialName("body") val body: String,
    @SerialName("type") val type: String,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("is_read") val isRead: Boolean,
    @SerialName("deep_link") val deepLink: String? = null,
    @SerialName("created_at") val createdAt: String
)
