package com.srmfood.gag.domain.model

data class Notification(
    val id: String,
    val title: String,
    val body: String,
    val type: NotificationType,
    val orderId: String?,
    val isRead: Boolean,
    val deepLink: String?,
    val createdAt: String
)

enum class NotificationType {
    GENERAL,
    ORDER_PLACED,
    ORDER_ACCEPTED,
    ORDER_PREPARING,
    ORDER_READY,
    ORDER_PICKED_UP,
    ORDER_CANCELLED,
    ORDER_REJECTED;

    companion object {
        fun fromString(type: String): NotificationType {
            return try {
                valueOf(type)
            } catch (e: Exception) {
                GENERAL
            }
        }
    }
}

// Mapper extension
fun com.srmfood.gag.data.remote.dto.NotificationDto.toDomain() = Notification(
    id = id,
    title = title,
    body = body,
    type = NotificationType.fromString(type),
    orderId = orderId,
    isRead = isRead,
    deepLink = deepLink,
    createdAt = createdAt
)
