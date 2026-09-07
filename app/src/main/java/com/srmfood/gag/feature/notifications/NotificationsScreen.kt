package com.srmfood.gag.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagBottomNavBar
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.studentBottomNavItems
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.Notification
import com.srmfood.gag.domain.model.NotificationType

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOrderClick: (String) -> Unit,
    onNavigateBottom: (String) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.notifications.collectAsState()

    Scaffold(
        bottomBar = {
            GagBottomNavBar(
                items = studentBottomNavItems,
                currentRoute = "notifications",
                onItemSelected = onNavigateBottom
            )
        },
        containerColor = GagBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        when (val uiState = state) {
            is UiState.Loading -> GagLoadingScreen(modifier = Modifier.padding(padding))

            is UiState.Error -> GagErrorScreen(
                message = uiState.message,
                onRetry = { /* ViewModel reloads on init */ },
                modifier = Modifier.padding(padding)
            )

            is UiState.Idle, is UiState.Empty -> NotificationsEmptyState(
                modifier = Modifier.padding(padding)
            )

            is UiState.Success -> {
                val notifications = uiState.data
                if (notifications.isEmpty()) {
                    NotificationsEmptyState(modifier = Modifier.padding(padding))
                } else {
                    val unreadCount = notifications.count { !it.isRead }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + 24.dp
                        )
                    ) {
                        // Header
                        item {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
                                    .statusBarsPadding()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column {
                                        Text(
                                            "Notifications",
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Stay up to date",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = GagPink
                                        )
                                    }
                                    if (unreadCount > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = GagPink
                                        ) {
                                            Text(
                                                text = "$unreadCount new",
                                                color = androidx.compose.ui.graphics.Color.White,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        items(notifications, key = { it.id }) { notification ->
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
    val isUnread = !notification.isRead
    val icon = notificationIcon(notification.type)
    val iconTint = notificationColor(notification.type)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isUnread) GagPink.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface,
        border = if (isUnread) androidx.compose.foundation.BorderStroke(1.dp, GagPink.copy(alpha = 0.3f)) else null,
        shadowElevation = if (isUnread) 3.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconTint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isUnread) FontWeight.ExtraBold else FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (isUnread) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp, start = 8.dp)
                                .size(8.dp)
                                .background(GagPink, CircleShape)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = notification.createdAt.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun NotificationsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.NotificationsNone,
            contentDescription = null,
            tint = GagPink.copy(alpha = 0.4f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "You're all caught up \u2728",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No new updates right now.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun notificationIcon(type: NotificationType): ImageVector = when (type) {
    NotificationType.ORDER_READY, NotificationType.ORDER_PICKED_UP -> Icons.Default.CheckCircle
    NotificationType.ORDER_CANCELLED, NotificationType.ORDER_REJECTED -> Icons.Default.ShoppingBag
    else -> Icons.Default.Notifications
}

private fun notificationColor(type: NotificationType) = when (type) {
    NotificationType.ORDER_READY, NotificationType.ORDER_PICKED_UP -> GagSuccess
    NotificationType.ORDER_CANCELLED, NotificationType.ORDER_REJECTED -> GagError
    NotificationType.ORDER_PLACED -> GagInfo
    NotificationType.ORDER_ACCEPTED -> GagInfo
    NotificationType.ORDER_PREPARING -> GagPink
    else -> GagOrange
}

