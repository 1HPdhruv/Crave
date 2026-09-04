package com.srmfood.gag.feature.vendor.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.GagBackground
import com.srmfood.gag.core.ui.theme.GagOnSurfaceVariant
import com.srmfood.gag.core.ui.theme.GagSurface
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.OrderStatus
import com.srmfood.gag.domain.usecase.order.GetVendorOrdersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VendorAnalyticsViewModel @Inject constructor(
    private val getVendorOrdersUseCase: GetVendorOrdersUseCase
) : ViewModel() {
    private val _orders = MutableStateFlow<UiState<List<Order>>>(UiState.Loading)
    val orders: StateFlow<UiState<List<Order>>> = _orders.asStateFlow()

    init {
        viewModelScope.launch {
            val res = getVendorOrdersUseCase()
            _orders.value = res.fold(onSuccess = { UiState.Success(it) }, onFailure = { UiState.Error(it.message ?: "Failed") })
        }
    }
}

@Composable
fun VendorAnalyticsScreen(
    onBack: () -> Unit,
    viewModel: VendorAnalyticsViewModel = hiltViewModel()
) {
    val ordersState by viewModel.orders.collectAsState()

    Scaffold(topBar = { GagTopBar("Analytics", onBack = onBack) }, containerColor = GagBackground) { padding ->
        when (val state = ordersState) {
            is UiState.Loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            is UiState.Success -> {
                val allOrders = state.data
                val completedOrders = allOrders.filter { it.status == OrderStatus.PICKED_UP }
                val totalRevenue = completedOrders.sumOf { it.total }

                // Calculate popular items
                val itemCounts = mutableMapOf<String, Int>()
                completedOrders.forEach { order ->
                    order.items.forEach { item ->
                        itemCounts[item.foodName] = (itemCounts[item.foodName] ?: 0) + item.quantity
                    }
                }
                val popularItems = itemCounts.entries.sortedByDescending { it.value }.take(5)

                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
                    item {
                        Text("Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("Total Revenue", "₹${totalRevenue.toInt()}", Modifier.weight(1f))
                            StatCard("Completed Orders", "${completedOrders.size}", Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    if (popularItems.isNotEmpty()) {
                        item {
                            Text("Top Selling Items", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        items(popularItems) { entry ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = GagSurface)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(entry.key, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text("${entry.value} sold", style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)
                                }
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
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = GagSurface, modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = GagOnSurfaceVariant)
        }
    }
}
