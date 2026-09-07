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
import androidx.compose.material.icons.filled.ArrowBack
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
        containerColor = GagBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        when (val state = orderState) {
            is UiState.Loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            is UiState.Error -> GagErrorScreen(message = state.message, onRetry = { viewModel.loadOrder(orderId) }, modifier = Modifier.padding(padding))
            is UiState.Success -> OrderDetailContent(
                order = state.data,
                onBack = onBack,
                onTrack = { onTrackOrder(orderId) },
                onShowQR = { onShowQR(orderId) },
                onCancel = { viewModel.cancelOrder(orderId) },
                modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)
            )
            else -> {}
        }
    }
}

@Composable
private fun OrderDetailContent(order: Order, onBack: () -> Unit, onTrack: () -> Unit, onShowQR: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val statusColor = order.status.color()

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        // Header
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).statusBarsPadding()) {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(order.orderNumber, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    Surface(shape = RoundedCornerShape(20.dp), color = statusColor.copy(alpha = 0.15f)) {
                        Text(order.status.displayName, color = statusColor, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
            }
        }

        // Outlet Info
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp), 
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Pickup from", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(order.outletName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    order.pickupSlot?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Scheduled: ${it.displayTime}", style = MaterialTheme.typography.bodySmall, color = GagPink, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Action buttons
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (order.status == OrderStatus.READY) {
                    GagPrimaryButton(
                        text = "Show Pickup QR Code",
                        onClick = onShowQR,
                        icon = Icons.Default.QrCode2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (order.status.isActive && order.status != OrderStatus.READY) {
                    GagPrimaryButton(
                        text = "Track Order", 
                        onClick = onTrack,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (order.status == OrderStatus.PLACED) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(16.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, GagError)
                    ) { Text("Cancel Order", color = GagError, fontWeight = FontWeight.Bold) }
                }
            }
        }

        // Cancellation reason
        if (!order.cancellationReason.isNullOrBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp), 
                    color = GagErrorContainer, 
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Cancellation Reason", style = MaterialTheme.typography.labelMedium, color = GagError, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(order.cancellationReason, style = MaterialTheme.typography.bodyMedium, color = GagError)
                    }
                }
            }
        }

        // Items
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(20.dp), 
                color = MaterialTheme.colorScheme.surface, 
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Order Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    order.items.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(GagSurfaceVariant)) {
                                GagFoodImage(
                                    model = item.foodImageUrl,
                                    contentDescription = item.foodName,
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = "${item.foodName} × ${item.quantity}", 
                                        style = MaterialTheme.typography.titleSmall, 
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text("₹${item.totalPrice.toInt()}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                                }
                                if (item.customizations.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "• " + item.customizations.joinToString(", "),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (index < order.items.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
        
        // Special Instructions
        if (!order.specialInstructions.isNullOrBlank()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp), 
                    color = MaterialTheme.colorScheme.surface, 
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Special Instructions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(order.specialInstructions, style = MaterialTheme.typography.bodyMedium, color = GagOrange)
                    }
                }
            }
        }

        // Payment Summary
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Payment Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subtotal", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("₹${order.subtotal.toInt()}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    if (order.tax > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("GST (5%)", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${order.tax.toInt()}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text("₹${order.total.toInt()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = GagPink)
                    }
                }
            }
        }
    }
}
