package com.srmfood.gag.domain.repository

import com.srmfood.gag.domain.model.Notification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun getNotifications(): Flow<List<Notification>>
    suspend fun markAsRead(notificationId: String): Result<Unit>
}
