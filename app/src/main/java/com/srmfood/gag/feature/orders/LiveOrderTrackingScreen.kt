package com.srmfood.gag.feature.orders

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagPrimaryButton
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.OrderStatus
import com.srmfood.gag.domain.usecase.order.ObserveOrderStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.usecase.order.GetOrderDetailsUseCase

@HiltViewModel
class LiveOrderTrackingViewModel @Inject constructor(
    private val getOrderDetailsUseCase: GetOrderDetailsUseCase,
    private val observeOrderStatusUseCase: ObserveOrderStatusUseCase
) : ViewModel() {

    private val _orderState = MutableStateFlow<UiState<Order>>(UiState.Loading)
    val orderState: StateFlow<UiState<Order>> = _orderState.asStateFlow()

    fun observeOrder(orderId: String) {
        viewModelScope.launch {
            _orderState.value = UiState.Loading
            val initial = getOrderDetailsUseCase(orderId)
            
            initial.onSuccess { order ->
                _orderState.value = UiState.Success(order)
                
                // Subscribe to realtime status changes
                observeOrderStatusUseCase(orderId).collectLatest { newStatus ->
                    val current = _orderState.value
                    if (current is UiState.Success) {
                        _orderState.value = UiState.Success(current.data.copy(status = newStatus))
                    }
                }
            }.onFailure { error ->
                _orderState.value = UiState.Error(error.message ?: "Order not found or connection error")
            }
        }
    }
}

// ─── Tracking steps ───────────────────────────────────────────────────────────
private val trackingSteps = listOf(
    OrderStatus.PLACED to "Order Placed",
    OrderStatus.ACCEPTED to "Accepted",
    OrderStatus.PREPARING to "Preparing",
    OrderStatus.READY to "Ready for Pickup!"
)

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun LiveOrderTrackingScreen(
    orderId: String,
    onBack: () -> Unit,
    onShowQR: (String) -> Unit,
    viewModel: LiveOrderTrackingViewModel = hiltViewModel()
) {
    val orderState by viewModel.orderState.collectAsState()

    LaunchedEffect(orderId) { viewModel.observeOrder(orderId) }

    Scaffold(
        topBar = { GagTopBar(title = "Order Tracking", onBack = onBack) },
        containerColor = GagBackground
    ) { padding ->
        when (val state = orderState) {
            is UiState.Idle, is UiState.Empty -> GagLoadingScreen(modifier = Modifier.padding(padding))
            is UiState.Loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            is UiState.Error -> com.srmfood.gag.core.ui.component.GagErrorScreen(
                message = state.message,
                onRetry = { viewModel.observeOrder(orderId) },
                modifier = Modifier.padding(padding)
            )
            is UiState.Success -> {
                val order = state.data
                val status = order.status

                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Order Info Card
                    Surface(shape = RoundedCornerShape(16.dp), color = GagSurface, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(order.orderNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("₹${order.total.toInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GagOrange)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(order.outletName, style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)
                            order.pickupSlot?.let {
                                Text("Pickup: ${it.displayTime}", style = MaterialTheme.typography.bodySmall, color = GagInfo)
                            }
                            Text("${order.items.size} item(s)", style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Pulsing status indicator
                    val isReady = status == OrderStatus.READY
                    val pulseAnim = rememberInfiniteTransition(label = "pulse")
                    val scale by pulseAnim.animateFloat(
                        initialValue = 1f, targetValue = if (isReady) 1f else 1.12f,
                        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
                        label = "scale"
                    )

                    Box(
                        modifier = Modifier.size(120.dp).scale(scale)
                            .background(if (isReady) GagSuccessContainer else GagOrange.copy(0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Circle,
                            contentDescription = null,
                            tint = if (isReady) GagSuccess else GagOrange,
                            modifier = Modifier.size(60.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = if (isReady) "Your order is ready! 🎉" else "Preparing your order…",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = status.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GagOnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Progress steps
            Surface(shape = RoundedCornerShape(16.dp), color = GagSurface, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    trackingSteps.forEachIndexed { index, (stepStatus, label) ->
                        val isDone = isStepDone(stepStatus, status)
                        val isCurrent = stepStatus == status

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(32.dp)
                                    .background(
                                        when {
                                            isDone -> GagSuccess
                                            isCurrent -> GagOrange
                                            else -> GagSurfaceVariant
                                        },
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isDone) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                } else {
                                    Text("${index + 1}", color = if (isCurrent) Color.White else GagOnSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isDone || isCurrent -> GagOnBackground
                                    else -> GagOnSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (status == OrderStatus.READY) {
                GagPrimaryButton(
                    text = "Show Pickup QR",
                    onClick = { onShowQR(orderId) },
                    icon = Icons.Default.QrCode2
                )
            }
            }
        }
    }
}
}

private fun isStepDone(stepStatus: OrderStatus, currentStatus: OrderStatus): Boolean {
    val order = listOf(OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY)
    return order.indexOf(stepStatus) < order.indexOf(currentStatus)
}
