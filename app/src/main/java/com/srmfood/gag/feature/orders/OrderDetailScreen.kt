package com.srmfood.gag.feature.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.component.GagFoodImage
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagPrimaryButton
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.OrderItem
import com.srmfood.gag.domain.model.OrderStatus
import com.srmfood.gag.domain.usecase.order.CancelOrderUseCase
import com.srmfood.gag.domain.usecase.order.GetOrderDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────
@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    private val getOrderDetailsUseCase: GetOrderDetailsUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase
) : ViewModel() {

    private val _order = MutableStateFlow<UiState<Order>>(UiState.Loading)
    val order: StateFlow<UiState<Order>> = _order.asStateFlow()

    fun loadOrder(orderId: String) {
        viewModelScope.launch {
            _order.value = UiState.Loading
            val result = getOrderDetailsUseCase(orderId)
            _order.value = result.fold(onSuccess = { UiState.Success(it) }, onFailure = { UiState.Error(it.message ?: "Failed") })
        }
    }

    fun cancelOrder(orderId: String) {
        viewModelScope.launch {
            val result = cancelOrderUseCase(orderId)
            result.onSuccess { loadOrder(orderId) }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun OrderDetailScreen(
    orderId: String,
    onBack: () -> Unit,
    onTrackOrder: (String) -> Unit,
    onShowQR: (String) -> Unit,
    viewModel: OrderDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(orderId) { viewModel.loadOrder(orderId) }
    val orderState by viewModel.order.collectAsState()

    Scaffold(
        topBar = { GagTopBar(title = "Order Details", onBack = onBack) },
        containerColor = GagBackground
    ) { padding ->
        when (val state = orderState) {
            is UiState.Loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            is UiState.Error -> GagErrorScreen(message = state.message, onRetry = { viewModel.loadOrder(orderId) }, modifier = Modifier.padding(padding))
            is UiState.Success -> OrderDetailContent(
                order = state.data,
                onTrack = { onTrackOrder(orderId) },
                onShowQR = { onShowQR(orderId) },
                onCancel = { viewModel.cancelOrder(orderId) },
                modifier = Modifier.padding(padding)
            )
            else -> {}
        }
    }
}

@Composable
private fun OrderDetailContent(order: Order, onTrack: () -> Unit, onShowQR: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val statusColor = order.status.color()

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Header
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = GagSurface, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(order.orderNumber, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Surface(shape = RoundedCornerShape(20.dp), color = statusColor.copy(alpha = 0.15f)) {
                            Text(order.status.displayName, color = statusColor, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(order.outletName, style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)
                    order.pickupSlot?.let {
                        Text("Pickup: ${it.displayTime}", style = MaterialTheme.typography.bodySmall, color = GagInfo)
                    }
                }
            }
        }

        // Items
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = GagSurface, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Items", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    order.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(GagSurfaceVariant)) {
                                GagFoodImage(
                                    model = item.foodImageUrl,
                                    contentDescription = item.foodName,
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${item.foodName} × ${item.quantity}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text("₹${item.totalPrice.toInt()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                if (item.customizations.isNotEmpty()) {
                                    Text(
                                        item.customizations.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GagOnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = GagOutlineVariant)
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subtotal", style = MaterialTheme.typography.bodyMedium)
                        Text("₹${order.subtotal.toInt()}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (order.tax > 0) {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tax & Fees", style = MaterialTheme.typography.bodyMedium)
                            Text("₹${order.tax.toInt()}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("₹${order.total.toInt()}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GagOrange)
                    }
                }
            }
        }
        
        // Special Instructions
        if (!order.specialInstructions.isNullOrBlank()) {
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = GagSurface, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Special Instructions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(order.specialInstructions, style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)
                    }
                }
            }
        }

        // Action buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (order.status == OrderStatus.READY) {
                    GagPrimaryButton(
                        text = "Show Pickup QR Code",
                        onClick = onShowQR,
                        icon = Icons.Default.QrCode2
                    )
                }
                if (order.status.isActive && order.status != OrderStatus.READY) {
                    GagPrimaryButton(text = "Track Order", onClick = onTrack)
                }
                if (order.status == OrderStatus.PLACED) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GagError)
                    ) { Text("Cancel Order", color = GagError) }
                }
            }
        }

        // Cancellation reason
        if (!order.cancellationReason.isNullOrBlank()) {
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = GagErrorContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Cancellation Reason", style = MaterialTheme.typography.labelMedium, color = GagError, fontWeight = FontWeight.Bold)
                        Text(order.cancellationReason, style = MaterialTheme.typography.bodySmall, color = GagError)
                    }
                }
            }
        }
    }
}
