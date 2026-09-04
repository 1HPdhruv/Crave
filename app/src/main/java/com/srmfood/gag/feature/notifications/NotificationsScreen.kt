package com.srmfood.gag.feature.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagEmptyScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.GagBackground
import com.srmfood.gag.core.ui.theme.GagOnSurfaceVariant
import com.srmfood.gag.core.ui.theme.GagOrange
import com.srmfood.gag.core.ui.theme.GagSurface
import com.srmfood.gag.domain.model.Notification
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOrderClick: (String) -> Unit,
    onNavigateBottom: (String) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.notifications.collectAsState()

    Scaffold(
        topBar = { GagTopBar("Notifications", onBack = onBack) },
        containerColor = GagBackground
    ) { padding ->
        when (val uiState = state) {
            is UiState.Idle, is UiState.Empty -> {
                GagEmptyScreen(title = "No notifications", modifier = Modifier.padding(padding))
            }
            is UiState.Loading -> GagLoadingScreen()
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                if (uiState.data.isEmpty()) {
                    GagEmptyScreen(title = "No notifications", modifier = Modifier.padding(padding))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.data, key = { it.id }) { notification ->
                            NotificationCard(
                                notification = notification,
                                onClick = {
                                    if (!notification.isRead) viewModel.markAsRead(notification.id)
                                    notification.orderId?.let { onOrderClick(it) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCard(
    notification: Notification,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = GagSurface,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Unread indicator dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (!notification.isRead) GagOrange else androidx.compose.ui.graphics.Color.Transparent,
                        shape = CircleShape
                    )
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GagOnSurfaceVariant
                )
            }
        }
    }
}
