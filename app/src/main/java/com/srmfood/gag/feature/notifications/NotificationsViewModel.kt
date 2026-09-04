package com.srmfood.gag.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.Notification
import com.srmfood.gag.domain.usecase.notification.GetNotificationsUseCase
import com.srmfood.gag.domain.usecase.notification.MarkNotificationReadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val getNotificationsUseCase: GetNotificationsUseCase,
    private val markNotificationReadUseCase: MarkNotificationReadUseCase
) : ViewModel() {

    private val _notifications = MutableStateFlow<UiState<List<Notification>>>(UiState.Loading)
    val notifications: StateFlow<UiState<List<Notification>>> = _notifications.asStateFlow()

    init {
        loadNotifications()
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            getNotificationsUseCase()
                .catch { e ->
                    _notifications.value = UiState.Error(e.message ?: "Failed to load notifications")
                }
                .collect { list ->
                    _notifications.value = UiState.Success(list)
                }
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            markNotificationReadUseCase(notificationId)
            // No need to manually refresh the list, the postgresChangeFlow will trigger an update automatically!
        }
    }
}
