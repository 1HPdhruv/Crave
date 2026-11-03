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
import com.srmfood.gag.core.ui.component.*
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagTopBar
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
    private val getOrdersUseCase: GetOrdersUseCase,
    private val refreshOrdersUseCase: com.srmfood.gag.domain.usecase.order.RefreshOrdersUseCase
) : ViewModel() {

    private val _orders = MutableStateFlow<UiState<List<Order>>>(UiState.Loading)
    val orders: StateFlow<UiState<List<Order>>> = _orders.asStateFlow()

    /** Set when the last server refresh failed while cached orders are still shown. */
    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    private var hasRefreshedOnce = false
    private var lastRefreshFailed = false
    private var cached: List<Order> = emptyList()

    init {
        viewModelScope.launch {
            getOrdersUseCase().collectLatest { list ->
                cached = list
                // Don't flash "No orders yet" before the first server answer.
                if (list.isNotEmpty()) _orders.value = UiState.Success(list)
                // A late cache emission must not turn a failed load into a reassuring "No orders yet".
                else if (hasRefreshedOnce && !lastRefreshFailed) _orders.value = UiState.Empty
            }
        }
        refresh()
    }

    /** Pulls fresh orders from the server (the cache alone never reflects vendor-side status changes). */
    fun refresh() {
        viewModelScope.launch {
            refreshOrdersUseCase().fold(
                onSuccess = {
                    hasRefreshedOnce = true
                    lastRefreshFailed = false
                    _refreshError.value = null
                    _orders.value = if (cached.isEmpty()) UiState.Empty else UiState.Success(cached)
                },
                onFailure = { _ ->
                    hasRefreshedOnce = true
                    lastRefreshFailed = true
                    _refreshError.value = "Couldn't refresh your orders. Showing the last saved list."
                    if (cached.isEmpty()) _orders.value = UiState.Error("Couldn't load your orders. Check your connection and try again.")
                }
            )
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun OrderHistoryScreen(
    onOrderClick: (String) -> Unit,
    onBrowseFood: () -> Unit,
    viewModel: OrderHistoryViewModel = hiltViewModel()
) {
    val ordersState by viewModel.orders.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()
    // Vendor-side changes (accepted/ready) arrive via the server only: refresh while this screen is visible.
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(20_000); viewModel.refresh() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        when (val state = ordersState) {
            is UiState.Loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            is UiState.Error -> com.srmfood.gag.core.ui.component.GagErrorScreen(
                message = state.message, onRetry = viewModel::refresh, modifier = Modifier.padding(padding)
            )
            is UiState.Empty -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CraveBlob(size = 76.dp, container = CraveLavender) {
                        Text("🧾", fontSize = 34.sp)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("No orders yet", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Your next craving is waiting.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(24.dp))
                    GagPrimaryButton(text = "Explore food  ↗", onClick = onBrowseFood, modifier = Modifier.width(220.dp))
                }
            }
            is UiState.Success -> {
                val active = state.data.filter { it.status.isActive }
                val past = state.data.filter { it.status.isTerminal }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)
                ) {
                    refreshError?.let { msg ->
                        item {
                            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.statusBarsPadding().padding(horizontal = 17.dp, vertical = 8.dp))
                        }
                    }
                    // Header
                    item {
                        Column(modifier = Modifier.padding(horizontal = 17.dp, vertical = 16.dp).statusBarsPadding()) {
                            CraveEyebrow("Your orders")
                            Text(
                                text = buildAnnotatedString {
                                    append("Order ")
                                    withStyle(SpanStyle(color = CraveOrange)) { append("tracker.") }
                                },
                                style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp, lineHeight = 40.sp),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    if (active.isNotEmpty()) {
                        item { SectionLabel("Current order") }
                        items(active, key = { it.id }) { order ->
                            OrderHistoryCard(order = order, isActive = true, onClick = { onOrderClick(order.id) })
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                    if (past.isNotEmpty()) {
                        item { SectionLabel("Order history") }
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
        text = label.uppercase(),
        style = GagEyebrowStyle,
        color = CraveOrangeDeep,
        modifier = Modifier.padding(horizontal = 17.dp, vertical = 12.dp)
    )
}

@Composable
private fun OrderHistoryCard(order: Order, isActive: Boolean, onClick: () -> Unit) {
    if (isActive) {
        // Website `.active-order-card`
        CraveBrutalCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 6.dp).clickable(onClick = onClick),
            container = CraveOrange
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    CraveSticker(order.status.displayName, container = CraveInk, content = CravePaper)
                    Text(order.orderNumber, style = GagMonoStyle.copy(fontSize = 11.sp), color = Color(0xFF6B2D20))
                }
                Spacer(Modifier.height(14.dp))
                Text(order.outletName, style = MaterialTheme.typography.headlineMedium, color = CraveInk, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (order.status) {
                        OrderStatus.PREPARING -> "Your food is being prepared."
                        OrderStatus.READY -> "Ready for pickup!"
                        else -> "Processing your order."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF65291D)
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        CraveEyebrow("${order.items.size} item(s)", color = Color(0xFF6B2D20))
                        CraveMono("₹${order.total.toInt()}", fontSize = 13f, color = CraveInk)
                    }
                    Text(
                        "Track ↗",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = CravePaper,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CraveInk)
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }
    } else {
        // Website `.history-row`
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 17.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CraveChartreuse)
                    .border(1.dp, CraveInk, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) { Text("🍽", fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(order.outletName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    "${order.orderNumber} · ${order.items.size} item(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                CraveMono("₹${order.total.toInt()}", fontSize = 12f)
                Spacer(Modifier.height(4.dp))
                CraveStateBadge(
                    order.status.displayName,
                    container = when (order.status) {
                        OrderStatus.CANCELLED, OrderStatus.REJECTED, OrderStatus.EXPIRED -> CraveRedSoft
                        else -> CraveChartreuse
                    }
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 17.dp), color = MaterialTheme.colorScheme.outline)
    }
}

fun OrderStatus.color() = when (this) {
    OrderStatus.PLACED -> GagInfo
    OrderStatus.ACCEPTED -> StatusAccepted
    OrderStatus.PREPARING -> StatusPreparing
    OrderStatus.READY -> OrbitSuccess
    OrderStatus.PICKED_UP -> GagSuccess
    OrderStatus.CANCELLED, OrderStatus.REJECTED -> GagError
    OrderStatus.EXPIRED -> GagOnSurfaceVariant
    else -> GagOnSurfaceVariant
}
