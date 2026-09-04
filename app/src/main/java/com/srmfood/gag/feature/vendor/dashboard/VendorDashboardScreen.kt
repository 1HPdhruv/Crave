package com.srmfood.gag.feature.vendor.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.OrderStatus
import com.srmfood.gag.domain.usecase.order.AcceptOrderUseCase
import com.srmfood.gag.domain.usecase.order.GetVendorOrdersUseCase
import com.srmfood.gag.domain.usecase.order.MarkReadyUseCase
import com.srmfood.gag.domain.usecase.order.RejectOrderUseCase
import com.srmfood.gag.domain.usecase.order.StartPreparingUseCase
import com.srmfood.gag.feature.orders.color
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────
@HiltViewModel
class VendorDashboardViewModel @Inject constructor(
    private val getVendorOrdersUseCase: GetVendorOrdersUseCase,
    private val acceptOrderUseCase: AcceptOrderUseCase,
    private val rejectOrderUseCase: RejectOrderUseCase,
    private val startPreparingUseCase: StartPreparingUseCase,
    private val markReadyUseCase: MarkReadyUseCase
) : ViewModel() {

    private val _orders = MutableStateFlow<UiState<List<Order>>>(UiState.Loading)
    val orders: StateFlow<UiState<List<Order>>> = _orders.asStateFlow()

    init { loadOrders() }

    fun loadOrders() {
        viewModelScope.launch {
            _orders.value = UiState.Loading
            val result = getVendorOrdersUseCase()
            _orders.value = result.fold(
                onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Failed") }
            )
        }
    }

    fun accept(orderId: String) = doAction { acceptOrderUseCase(orderId) }
    fun reject(orderId: String) = doAction { rejectOrderUseCase(orderId, "Rejected by vendor") }
    fun startPreparing(orderId: String) = doAction { startPreparingUseCase(orderId) }
    fun markReady(orderId: String) = doAction { markReadyUseCase(orderId) }

    private fun doAction(action: suspend () -> Result<Order>) {
        viewModelScope.launch { action(); loadOrders() }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun VendorDashboardScreen(
    onOrderClick: (String) -> Unit,
    onScanQR: () -> Unit,
    onAllOrders: () -> Unit,
    onMenu: () -> Unit,
    onLogout: () -> Unit,
    viewModel: VendorDashboardViewModel = hiltViewModel()
) {
    val ordersState by viewModel.orders.collectAsState()

    Scaffold(
        containerColor = GagBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Vendor Dashboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("SRM KTR", style = MaterialTheme.typography.bodySmall, color = GagOrange)
                    }
                },
                actions = {
                    IconButton(onClick = onMenu) { Icon(Icons.Outlined.MenuBook, "Menu", tint = GagOnBackground) }
                    IconButton(onClick = onAllOrders) { Icon(Icons.Outlined.Assignment, "Orders", tint = GagOnBackground) }
                    IconButton(onClick = onLogout) { Icon(Icons.Outlined.Logout, "Logout", tint = GagError) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GagBackground)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanQR,
                containerColor = GagOrange,
                contentColor = androidx.compose.ui.graphics.Color.White,
                icon = { Icon(Icons.Filled.QrCodeScanner, "Scan QR") },
                text = { Text("Scan Pickup", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        when (val state = ordersState) {
            is UiState.Loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            is UiState.Success -> {
                val activeOrders = state.data.filter { it.status.isActive }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        // Stats row
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard("Pending", state.data.count { it.status == OrderStatus.PLACED }.toString(), GagInfo, modifier = Modifier.weight(1f))
                            StatCard("Preparing", state.data.count { it.status == OrderStatus.PREPARING }.toString(), GagAmber, modifier = Modifier.weight(1f))
                            StatCard("Ready", state.data.count { it.status == OrderStatus.READY }.toString(), GagSuccess, modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Incoming Orders", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    items(activeOrders, key = { it.id }) { order ->
                        VendorOrderCard(
                            order = order,
                            onClick = { onOrderClick(order.id) },
                            onAccept = { viewModel.accept(order.id) },
                            onReject = { viewModel.reject(order.id) },
                            onStartPreparing = { viewModel.startPreparing(order.id) },
                            onMarkReady = { viewModel.markReady(order.id) }
                        )
                    }
                    if (activeOrders.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text("No active orders right now 🎉", style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)
                            }
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.12f), modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = GagOnSurfaceVariant)
        }
    }
}

@Composable
private fun VendorOrderCard(
    order: Order,
    onClick: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onStartPreparing: () -> Unit,
    onMarkReady: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = GagSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(order.orderNumber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(20.dp), color = order.status.color().copy(0.15f)) {
                    Text(order.status.displayName, style = MaterialTheme.typography.labelSmall, color = order.status.color(),
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("${order.items.size} item(s)  ·  ₹${order.total.toInt()}", style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
            order.pickupSlot?.let { Text("Pickup: ${it.displayTime}", style = MaterialTheme.typography.bodySmall, color = GagInfo) }
            order.specialInstructions?.let { Text("Note: $it", style = MaterialTheme.typography.bodySmall, color = GagAmber) }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (order.status) {
                    OrderStatus.PLACED -> {
                        Button(onClick = onAccept, colors = ButtonDefaults.buttonColors(containerColor = GagSuccess), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) { Text("Accept") }
                        OutlinedButton(onClick = onReject, border = androidx.compose.foundation.BorderStroke(1.dp, GagError), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) { Text("Reject", color = GagError) }
                    }
                    OrderStatus.ACCEPTED -> {
                        Button(onClick = onStartPreparing, colors = ButtonDefaults.buttonColors(containerColor = GagAmber), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Text("Start Preparing") }
                    }
                    OrderStatus.PREPARING -> {
                        Button(onClick = onMarkReady, colors = ButtonDefaults.buttonColors(containerColor = GagOrange), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Text("Mark Ready") }
                    }
                    else -> {}
                }
            }
        }
    }
}
