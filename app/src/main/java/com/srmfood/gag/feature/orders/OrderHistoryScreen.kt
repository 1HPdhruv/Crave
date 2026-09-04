package com.srmfood.gag.feature.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
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
import com.srmfood.gag.core.ui.component.GagBottomNavBar
import com.srmfood.gag.core.ui.component.GagEmptyScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.component.studentBottomNavItems
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.OrderStatus
import com.srmfood.gag.domain.usecase.order.GetOrdersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────
@HiltViewModel
class OrderHistoryViewModel @Inject constructor(
    private val getOrdersUseCase: GetOrdersUseCase
) : ViewModel() {

    private val _orders = MutableStateFlow<UiState<List<Order>>>(UiState.Loading)
    val orders: StateFlow<UiState<List<Order>>> = _orders.asStateFlow()

    init {
        viewModelScope.launch {
            getOrdersUseCase().collectLatest { list ->
                _orders.value = if (list.isEmpty()) UiState.Empty else UiState.Success(list)
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun OrderHistoryScreen(
    onBack: () -> Unit,
    onOrderClick: (String) -> Unit,
    onNavigateBottom: (String) -> Unit,
    viewModel: OrderHistoryViewModel = hiltViewModel()
) {
    val ordersState by viewModel.orders.collectAsState()

    Scaffold(
        topBar = { GagTopBar(title = "My Orders", onBack = onBack) },
        bottomBar = { GagBottomNavBar(items = studentBottomNavItems, currentRoute = "orders", onItemSelected = onNavigateBottom) },
        containerColor = GagBackground
    ) { padding ->
        when (val state = ordersState) {
            is UiState.Loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            is UiState.Empty -> GagEmptyScreen(title = "No orders yet", message = "Your order history will appear here", modifier = Modifier.padding(padding))
            is UiState.Success -> {
                val active = state.data.filter { it.status.isActive }
                val past = state.data.filter { it.status.isTerminal }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (active.isNotEmpty()) {
                        item { SectionLabel("Active Orders") }
                        items(active, key = { it.id }) { order ->
                            OrderHistoryCard(order = order, onClick = { onOrderClick(order.id) })
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                    if (past.isNotEmpty()) {
                        item { SectionLabel("Past Orders") }
                        items(past, key = { it.id }) { order ->
                            OrderHistoryCard(order = order, onClick = { onOrderClick(order.id) })
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun OrderHistoryCard(order: Order, onClick: () -> Unit) {
    val statusColor = order.status.color()
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = GagSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(order.orderNumber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Circle, null, tint = statusColor, modifier = Modifier.size(8.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(order.status.displayName, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(order.outletName, style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                Text("${order.items.size} item(s)  ·  ₹${order.total.toInt()}", style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                // Add Date/Time and Pickup Slot info
                Text(order.createdAt.take(10), style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                order.pickupSlot?.let {
                    Text("Pickup: ${it.displayTime}", style = MaterialTheme.typography.labelSmall, color = GagInfo)
                }
            }
            Text("₹${order.total.toInt()}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GagOrange)
        }
    }
}

fun OrderStatus.color() = when (this) {
    OrderStatus.PLACED -> GagInfo
    OrderStatus.ACCEPTED -> StatusAccepted
    OrderStatus.PREPARING -> StatusPreparing
    OrderStatus.READY -> StatusReady
    OrderStatus.PICKED_UP -> GagSuccess
    OrderStatus.CANCELLED, OrderStatus.REJECTED -> GagError
    OrderStatus.EXPIRED -> GagOnSurfaceVariant
    else -> GagOnSurfaceVariant
}
