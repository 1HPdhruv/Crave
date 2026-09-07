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
        bottomBar = { GagBottomNavBar(items = studentBottomNavItems, currentRoute = "orders", onItemSelected = onNavigateBottom) },
        containerColor = GagBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        when (val state = ordersState) {
            is UiState.Loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            is UiState.Empty -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No orders yet 🍽️", 
                        style = MaterialTheme.typography.headlineMedium, 
                        fontWeight = FontWeight.ExtraBold, 
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your next craving is waiting.", 
                        style = MaterialTheme.typography.bodyLarge, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    com.srmfood.gag.core.ui.component.GagPrimaryButton(
                        text = "Explore Food",
                        onClick = { onNavigateBottom("home") }
                    )
                }
            }
            is UiState.Success -> {
                val active = state.data.filter { it.status.isActive }
                val past = state.data.filter { it.status.isTerminal }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)
                ) {
                    // Header
                    item {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).statusBarsPadding()) {
                            Text("Your Orders", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Track your cravings", style = MaterialTheme.typography.titleMedium, color = GagPink)
                        }
                    }

                    if (active.isNotEmpty()) {
                        item { SectionLabel("CURRENT ORDER") }
                        items(active, key = { it.id }) { order ->
                            OrderHistoryCard(order = order, isActive = true, onClick = { onOrderClick(order.id) })
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                    if (past.isNotEmpty()) {
                        item { SectionLabel("PAST ORDERS") }
                        items(past, key = { it.id }) { order ->
                            OrderHistoryCard(order = order, isActive = false, onClick = { onOrderClick(order.id) })
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
    Text(
        text = label, 
        style = MaterialTheme.typography.labelMedium, 
        fontWeight = FontWeight.ExtraBold, 
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun OrderHistoryCard(order: Order, isActive: Boolean, onClick: () -> Unit) {
    val statusColor = order.status.color()
    val bgColor = if (isActive) GagPink.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
    val outlineColor = if (isActive) GagPink.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, outlineColor),
        shadowElevation = if (isActive) 4.dp else 2.dp
    ) {
        Column(modifier = Modifier.clickable(onClick = onClick).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(order.outletName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(order.orderNumber, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = RoundedCornerShape(20.dp), 
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = order.status.displayName, 
                        color = statusColor, 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall, 
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Circle, null, tint = statusColor, modifier = Modifier.size(8.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (order.status) {
                            OrderStatus.PREPARING -> "Your food is being prepared."
                            OrderStatus.READY -> "Ready for pickup!"
                            else -> "Processing your order."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("${order.items.size} item(s)  ·  ₹${order.total.toInt()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(order.createdAt.take(10), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (isActive) {
                    com.srmfood.gag.core.ui.component.GagPrimaryButton(
                        text = "Track Order",
                        onClick = onClick,
                        modifier = Modifier.height(36.dp)
                    )
                } else {
                    TextButton(onClick = onClick, modifier = Modifier.padding(0.dp)) {
                        Text("View Details", color = GagPink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

fun OrderStatus.color() = when (this) {
    OrderStatus.PLACED -> GagInfo
    OrderStatus.ACCEPTED -> StatusAccepted
    OrderStatus.PREPARING -> GagPink
    OrderStatus.READY -> GagSuccess
    OrderStatus.PICKED_UP -> GagSuccess
    OrderStatus.CANCELLED, OrderStatus.REJECTED -> GagError
    OrderStatus.EXPIRED -> GagOnSurfaceVariant
    else -> GagOnSurfaceVariant
}
