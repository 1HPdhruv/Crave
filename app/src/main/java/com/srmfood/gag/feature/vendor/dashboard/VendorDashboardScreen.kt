package com.srmfood.gag.feature.vendor.dashboard

import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material.icons.filled.Star
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
import com.srmfood.gag.core.ui.component.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
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

    /** Set when an accept/reject/prepare/ready action failed (e.g. the order changed meanwhile). */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()
    fun dismissActionError() { _actionError.value = null }

    /** [silent] = background refresh: keep the list on screen and don't blank it on a transient failure. */
    fun loadOrders(silent: Boolean = false) {
        viewModelScope.launch {
            val hadData = _orders.value is UiState.Success
            if (!silent || !hadData) _orders.value = UiState.Loading
            val result = getVendorOrdersUseCase()
            result.fold(
                onSuccess = { _orders.value = if (it.isEmpty()) UiState.Empty else UiState.Success(it) },
                onFailure = { if (!(silent && hadData)) _orders.value = UiState.Error(it.message ?: "Failed") }
            )
        }
    }

    fun accept(orderId: String) = doAction { acceptOrderUseCase(orderId) }
    fun reject(orderId: String) = doAction { rejectOrderUseCase(orderId, "Rejected by vendor") }
    fun startPreparing(orderId: String) = doAction { startPreparingUseCase(orderId) }
    fun markReady(orderId: String) = doAction { markReadyUseCase(orderId) }

    private fun doAction(action: suspend () -> Result<Order>) {
        viewModelScope.launch {
            action().onFailure { _actionError.value = it.message ?: "That action couldn't be completed. The order may have changed." }
            loadOrders(silent = true) // always show the order's real state afterwards
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun VendorDashboardScreen(
    onOrderClick: (String) -> Unit,
    onScanQR: () -> Unit,
    onAllOrders: () -> Unit,
    onReviews: () -> Unit,
    onMenu: () -> Unit,
    onLogout: () -> Unit,
    onInventory: () -> Unit = {},
    onOutlet: () -> Unit = {},
    onAnalytics: () -> Unit = {},
    onProfile: () -> Unit = {},
    viewModel: VendorDashboardViewModel = hiltViewModel()
) {
    val ordersState by viewModel.orders.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    // New paid orders appear server-side only: poll while the dashboard is on screen.
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(15_000); viewModel.loadOrders(silent = true) } }
    actionError?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::dismissActionError,
            title = { androidx.compose.material3.Text("Action not completed") },
            text = { androidx.compose.material3.Text(msg) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = viewModel::dismissActionError) { androidx.compose.material3.Text("OK") } }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        CraveEyebrow("Vendor workspace · SRMIST", color = CraveOrangeDeep)
                        Text("Order board", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onMenu) { Icon(Icons.Outlined.MenuBook, "Menu", tint = MaterialTheme.colorScheme.onSurface) }
                    IconButton(onClick = onAllOrders) { Icon(Icons.Outlined.Assignment, "Orders", tint = MaterialTheme.colorScheme.onSurface) }
                    IconButton(onClick = onReviews) { Icon(Icons.Filled.Star, "Reviews", tint = MaterialTheme.colorScheme.onSurface) }
                    var more by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurface) }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(text = { Text("Inventory") }, onClick = { more = false; onInventory() })
                            DropdownMenuItem(text = { Text("Outlet & pickup slots") }, onClick = { more = false; onOutlet() })
                            DropdownMenuItem(text = { Text("Analytics") }, onClick = { more = false; onAnalytics() })
                            DropdownMenuItem(text = { Text("Profile") }, onClick = { more = false; onProfile() })
                            DropdownMenuItem(text = { Text("Log out", color = GagError) }, onClick = { more = false; onLogout() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanQR,
                containerColor = CraveOrange,
                contentColor = CraveInk,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(1.5.dp, CraveInk, RoundedCornerShape(12.dp)),
                icon = { Icon(Icons.Filled.QrCodeScanner, "Scan QR") },
                text = { Text("Scan Pickup", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        when (val state = ordersState) {
            is UiState.Loading -> GagLoadingScreen()
            is UiState.Success -> {
                val activeOrders = state.data.filter { it.status.isActive }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(17.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        // Stats row
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard("New", state.data.count { it.status == OrderStatus.PLACED }.toString(), CraveOrange, modifier = Modifier.weight(1f))
                            StatCard("Cooking", state.data.count { it.status == OrderStatus.PREPARING }.toString(), CraveAqua, modifier = Modifier.weight(1f))
                            StatCard("Ready", state.data.count { it.status == OrderStatus.READY }.toString(), CraveChartreuse, modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        CraveHeading(eyebrow = "Right now", title = "Incoming orders")
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
                                Text("No active orders right now", style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)
                            }
                        }
                    }
                }
            }
            is UiState.Empty -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(17.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard("New", "0", CraveOrange, modifier = Modifier.weight(1f))
                            StatCard("Cooking", "0", CraveAqua, modifier = Modifier.weight(1f))
                            StatCard("Ready", "0", CraveChartreuse, modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("No active orders right now", style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)
                        }
                    }
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Unable to load your dashboard.", style = MaterialTheme.typography.titleMedium, color = GagError)
                        Spacer(modifier = Modifier.height(16.dp))
                        GagPrimaryButton(text = "Retry", onClick = { viewModel.loadOrders() }, modifier = Modifier.width(160.dp))
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading dashboard...", style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)
                }
            }
        }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = color,
        border = androidx.compose.foundation.BorderStroke(1.dp, CraveInk),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            CraveMono(value, fontSize = 30f, color = CraveInk)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = CraveInk)
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                CraveMono(order.orderNumber, fontSize = 12f)
                CraveStateBadge(
                    order.status.displayName,
                    container = when (order.status) {
                        OrderStatus.PLACED -> CraveOrange
                        OrderStatus.ACCEPTED, OrderStatus.PREPARING -> CraveAqua
                        else -> CraveChartreuse
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("${order.items.size} item(s)  ·  ₹${order.total.toInt()}", style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
            order.pickupSlot?.let { Text("Pickup: ${it.displayTime}", style = MaterialTheme.typography.bodySmall, color = GagInfo) }
            order.specialInstructions?.let { Text("Note: $it", style = MaterialTheme.typography.bodySmall, color = CraveOrangeDeep) }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (order.status) {
                    OrderStatus.PLACED -> {
                        Button(onClick = onAccept, colors = ButtonDefaults.buttonColors(containerColor = CraveChartreuse, contentColor = CraveInk), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, CraveInk), modifier = Modifier.weight(1f)) { Text("Accept") }
                        OutlinedButton(onClick = onReject, border = androidx.compose.foundation.BorderStroke(1.dp, GagError), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) { Text("Reject", color = GagError) }
                    }
                    OrderStatus.ACCEPTED -> {
                        Button(onClick = onStartPreparing, colors = ButtonDefaults.buttonColors(containerColor = CraveAqua, contentColor = CraveInk), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, CraveInk), modifier = Modifier.fillMaxWidth()) { Text("Start Preparing") }
                    }
                    OrderStatus.PREPARING -> {
                        Button(onClick = onMarkReady, colors = ButtonDefaults.buttonColors(containerColor = CraveChartreuse, contentColor = CraveInk), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, CraveInk), modifier = Modifier.fillMaxWidth()) { Text("Mark Ready") }
                    }
                    else -> {}
                }
            }
        }
    }
}
