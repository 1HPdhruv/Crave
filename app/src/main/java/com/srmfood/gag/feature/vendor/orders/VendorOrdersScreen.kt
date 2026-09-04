package com.srmfood.gag.feature.vendor.orders

import androidx.compose.foundation.clickable
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
import com.srmfood.gag.core.ui.component.GagEmptyScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.usecase.order.GetVendorOrdersUseCase
import com.srmfood.gag.feature.orders.color
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VendorOrdersViewModel @Inject constructor(
    private val getVendorOrdersUseCase: GetVendorOrdersUseCase
) : ViewModel() {
    private val _orders = MutableStateFlow<UiState<List<Order>>>(UiState.Loading)
    val orders: StateFlow<UiState<List<Order>>> = _orders.asStateFlow()

    init {
        viewModelScope.launch {
            val result = getVendorOrdersUseCase()
            _orders.value = result.fold(
                onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Failed") }
            )
        }
    }
}

@Composable
fun VendorOrdersScreen(
    onBack: () -> Unit,
    onOrderClick: (String) -> Unit,
    viewModel: VendorOrdersViewModel = hiltViewModel()
) {
    val ordersState by viewModel.orders.collectAsState()

    Scaffold(
        topBar = { GagTopBar(title = "All Orders", onBack = onBack) },
        containerColor = GagBackground
    ) { padding ->
        when (val state = ordersState) {
            is UiState.Loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            is UiState.Empty -> GagEmptyScreen(title = "No orders found", modifier = Modifier.padding(padding))
            is UiState.Success -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.data, key = { it.id }) { order ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onOrderClick(order.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = GagSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(order.orderNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("${order.items.size} item(s) · ₹${order.total.toInt()}", style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                                }
                                Surface(shape = RoundedCornerShape(8.dp), color = order.status.color().copy(0.15f)) {
                                    Text(order.status.displayName, style = MaterialTheme.typography.labelSmall, color = order.status.color(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
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
