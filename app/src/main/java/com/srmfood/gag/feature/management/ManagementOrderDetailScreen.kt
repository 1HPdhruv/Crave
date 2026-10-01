package com.srmfood.gag.feature.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.theme.OrbitLime
import com.srmfood.gag.domain.model.Order
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementOrderDetailScreen(
    onBack: () -> Unit,
    viewModel: ManagementOrderDetailViewModel = hiltViewModel()
) {
    val orderState by viewModel.orderState.collectAsState()
    val currencyFormatter = NumberFormat.getInstance(Locale("en", "IN"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order Detail", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = OrbitLime,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = orderState) {
                is UiState.Loading -> {
                    CircularProgressIndicator(color = OrbitLime, modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Error", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                    }
                }
                is UiState.Success -> {
                    val order = state.data
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        item {
                            OrderHeaderSection(order)
                        }
                        item {
                            OutletSection(order)
                        }
                        item {
                            CustomerSection(order)
                        }
                        item {
                            ItemsSection(order, currencyFormatter)
                        }
                        item {
                            PaymentSection(order, currencyFormatter)
                        }
                        item {
                            PickupSection(order)
                        }
                        item {
                            TimelineSection(order)
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BaseCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun OrderHeaderSection(order: Order) {
    SectionTitle("ORDER INFORMATION")
    BaseCard {
        Text(order.orderNumber, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        InfoRow("Status", order.status.name)
        InfoRow("Order ID", order.id)
        InfoRow("Created", order.createdAt.take(19).replace("T", " "))
    }
}

@Composable
private fun OutletSection(order: Order) {
    SectionTitle("OUTLET")
    BaseCard {
        InfoRow("Outlet Name", order.outletName)
        InfoRow("Outlet ID", order.outletId)
        InfoRow("Vendor ID", order.vendorId)
    }
}

@Composable
private fun CustomerSection(order: Order) {
    SectionTitle("CUSTOMER")
    BaseCard {
        InfoRow("User ID", order.userId)
    }
}

@Composable
private fun ItemsSection(order: Order, formatter: NumberFormat) {
    SectionTitle("ITEMS")
    BaseCard {
        order.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${item.foodName} × ${item.quantity}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    if (item.customizations.isNotEmpty()) {
                        Text(item.customizations.joinToString(", "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("₹${formatter.format(item.totalPrice)}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            }
        }
        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow("Subtotal", "₹${formatter.format(order.subtotal)}")
        InfoRow("Tax", "₹${formatter.format(order.tax)}")
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text("₹${formatter.format(order.total)}", color = OrbitLime, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PaymentSection(order: Order, formatter: NumberFormat) {
    SectionTitle("PAYMENT")
    BaseCard {
        InfoRow("Method", order.paymentMethod.displayName)
        InfoRow("Status", order.paymentStatus.name)
        InfoRow("Amount", "₹${formatter.format(order.total)}")
    }
}

@Composable
private fun PickupSection(order: Order) {
    SectionTitle("PICKUP")
    BaseCard {
        order.pickupSlot?.let { slot ->
            InfoRow("Date", slot.date)
            InfoRow("Slot", "${slot.startTime} - ${slot.endTime}")
        } ?: run {
            Text("No specific pickup slot assigned", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (order.qrToken != null) {
            InfoRow("Token Assigned", "Yes")
        }
    }
}

@Composable
private fun TimelineSection(order: Order) {
    SectionTitle("TIMELINE")
    BaseCard {
        order.placedAt?.let { InfoRow("Placed", it.take(19).replace("T", " ")) }
        order.acceptedAt?.let { InfoRow("Accepted", it.take(19).replace("T", " ")) }
        order.preparingAt?.let { InfoRow("Preparing", it.take(19).replace("T", " ")) }
        order.readyAt?.let { InfoRow("Ready", it.take(19).replace("T", " ")) }
        order.pickedUpAt?.let { InfoRow("Picked Up", it.take(19).replace("T", " ")) }
        order.cancelledAt?.let { 
            InfoRow("Cancelled", it.take(19).replace("T", " "))
            if (order.cancellationReason != null) {
                InfoRow("Reason", order.cancellationReason)
            }
        }
    }
}
