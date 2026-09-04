package com.srmfood.gag.domain.usecase.notification

import com.srmfood.gag.domain.model.Notification
import com.srmfood.gag.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetNotificationsUseCase @Inject constructor(
    private val repository: NotificationRepository
) {
    operator fun invoke(): Flow<List<Notification>> = repository.getNotifications()
}

class MarkNotificationReadUseCase @Inject constructor(
    private val repository: NotificationRepository
) {
    suspend operator fun invoke(notificationId: String): Result<Unit> = repository.markAsRead(notificationId)
}
