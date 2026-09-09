package com.srmfood.gag.feature.orders

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ArrowBack
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
        containerColor = GagBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
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
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Custom Header
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).statusBarsPadding().fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val pulseAnim = rememberInfiniteTransition(label = "live_pulse")
                                val alpha by pulseAnim.animateFloat(
                                    initialValue = 0.4f, targetValue = 1f,
                                    animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
                                    label = "live_alpha"
                                )
                                Icon(Icons.Default.Circle, contentDescription = "Live", tint = GagPink.copy(alpha = alpha), modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Live updates", style = MaterialTheme.typography.labelMedium, color = GagPink, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                        // Order Info Card
                        Surface(
                            shape = RoundedCornerShape(20.dp), 
                            color = MaterialTheme.colorScheme.surfaceVariant, 
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(order.orderNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                    Text("₹${order.total.toInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = GagPink)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(order.outletName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                order.pickupSlot?.let {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Pickup: ${it.displayTime}", style = MaterialTheme.typography.bodySmall, color = GagPink, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Pulsing status indicator
                        val isReady = status == OrderStatus.READY
                        val pulseAnim = rememberInfiniteTransition(label = "pulse")
                        val scale by pulseAnim.animateFloat(
                            initialValue = 1f, targetValue = if (isReady) 1f else 1.12f,
                            animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
                            label = "scale"
                        )

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier.size(140.dp).scale(scale)
                                    .background(if (isReady) GagSuccessContainer else GagPink.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Circle,
                                    contentDescription = null,
                                    tint = if (isReady) GagSuccess else GagPink,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Text(
                            text = if (isReady) "Your order is ready" else "Preparing your order…",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = status.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Progress steps
                        Surface(
                            shape = RoundedCornerShape(20.dp), 
                            color = MaterialTheme.colorScheme.surface, 
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                trackingSteps.forEachIndexed { index, (stepStatus, label) ->
                                    val isDone = isStepDone(stepStatus, status)
                                    val isCurrent = stepStatus == status

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(36.dp)
                                                .background(
                                                    when {
                                                        isDone -> GagSuccess
                                                        isCurrent -> GagPink
                                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                                    },
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isDone) {
                                                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                            } else {
                                                Text("${index + 1}", color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isCurrent || isDone) FontWeight.Bold else FontWeight.Medium,
                                            color = when {
                                                isDone || isCurrent -> MaterialTheme.colorScheme.onBackground
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        if (status == OrderStatus.READY) {
                            GagPrimaryButton(
                                text = "Show Pickup QR Code",
                                onClick = { onShowQR(orderId) },
                                icon = Icons.Default.QrCode2,
                                modifier = Modifier.fillMaxWidth().padding(bottom = padding.calculateBottomPadding() + 24.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(padding.calculateBottomPadding() + 24.dp))
                        }
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
