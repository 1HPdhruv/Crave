package com.srmfood.gag.feature.vendor.orders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagPrimaryButton
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.OrderStatus
import com.srmfood.gag.domain.usecase.order.AcceptOrderUseCase
import com.srmfood.gag.domain.usecase.order.GetOrderDetailsUseCase
import com.srmfood.gag.domain.usecase.order.MarkReadyUseCase
import com.srmfood.gag.domain.usecase.order.RejectOrderUseCase
import com.srmfood.gag.domain.usecase.order.StartPreparingUseCase
import com.srmfood.gag.feature.orders.color
import com.srmfood.gag.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VendorOrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getOrderDetailsUseCase: GetOrderDetailsUseCase,
    private val acceptOrderUseCase: AcceptOrderUseCase,
    private val rejectOrderUseCase: RejectOrderUseCase,
    private val startPreparingUseCase: StartPreparingUseCase,
    private val markReadyUseCase: MarkReadyUseCase
) : ViewModel() {
    private val orderId = savedStateHandle.get<String>(Screen.VendorOrderDetail.ARG_ORDER_ID) ?: ""
    private val _order = MutableStateFlow<UiState<Order>>(UiState.Loading)
    val order: StateFlow<UiState<Order>> = _order.asStateFlow()

    init { loadOrder() }

    private fun loadOrder() {
        viewModelScope.launch {
            val res = getOrderDetailsUseCase(orderId)
            _order.value = res.fold(onSuccess = { UiState.Success(it) }, onFailure = { UiState.Error(it.message ?: "Error") })
        }
    }

    fun accept() { viewModelScope.launch { acceptOrderUseCase(orderId); loadOrder() } }
    fun reject() { viewModelScope.launch { rejectOrderUseCase(orderId, "Rejected"); loadOrder() } }
    fun startPreparing() { viewModelScope.launch { startPreparingUseCase(orderId); loadOrder() } }
    fun markReady() { viewModelScope.launch { markReadyUseCase(orderId); loadOrder() } }
}

@Composable
fun VendorOrderDetailScreen(
    orderId: String,
    onBack: () -> Unit,
    viewModel: VendorOrderDetailViewModel = hiltViewModel()
) {
    val orderState by viewModel.order.collectAsState()

    Scaffold(
        topBar = { GagTopBar(title = "Order Details", onBack = onBack) },
        containerColor = GagBackground
    ) { padding ->
        when (val state = orderState) {
            is UiState.Loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            is UiState.Success -> {
                val order = state.data
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = GagSurface, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Order ${order.orderNumber}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(order.status.displayName, color = order.status.color(), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Pickup: ${order.pickupSlot?.displayTime ?: "N/A"}", style = MaterialTheme.typography.bodyMedium)
                                order.specialInstructions?.let {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Note: $it", style = MaterialTheme.typography.bodyMedium, color = GagAmber)
                                }
                            }
                        }
                    }
                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = GagSurface, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Items", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                order.items.forEach { item ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${item.foodName} × ${item.quantity}")
                                        Text("₹${item.totalPrice.toInt()}")
                                    }
                                }
                            }
                        }
                    }
                    item {
                        when (order.status) {
                            OrderStatus.PLACED -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = viewModel::accept, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = GagSuccess)) { Text("Accept") }
                                    OutlinedButton(onClick = viewModel::reject, modifier = Modifier.weight(1f)) { Text("Reject", color = GagError) }
                                }
                            }
                            OrderStatus.ACCEPTED -> GagPrimaryButton(text = "Start Preparing", onClick = viewModel::startPreparing)
                            OrderStatus.PREPARING -> GagPrimaryButton(text = "Mark Ready", onClick = viewModel::markReady)
                            else -> {}
                        }
                    }
                }
            }
            else -> {}
        }
    }
}
