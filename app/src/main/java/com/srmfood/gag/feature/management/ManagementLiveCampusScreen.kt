package com.srmfood.gag.feature.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.Outlet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementLiveCampusScreen(
    viewModel: ManagementLiveCampusViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Live Campus")
                        Text(
                            text = "Current campus operations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Error: Management data is temporarily unavailable.", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.refresh() }) {
                        Text("Retry")
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PaddingValues(16.dp)
                            Text(
                                text = "Live Campus currently uses manual refresh because Management does not yet have a dedicated Realtime aggregation stream. Tap the refresh icon for the latest snapshot.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    if (uiState.alerts.isNotEmpty()) {
                        item {
                            Text("Operational Alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            uiState.alerts.forEach { alert ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(alert, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    item {
                        Text("Active Orders (${uiState.totalActiveOrders})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            MetricBox("Placed", uiState.placedOrders.toString())
                            MetricBox("Accepted", uiState.acceptedOrders.toString())
                            MetricBox("Preparing", uiState.preparingOrders.toString())
                            MetricBox("Ready", uiState.readyOrders.toString())
                        }
                    }

                    item {
                        Text("Outlets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            MetricBox("Open", uiState.openOutlets.toString())
                            MetricBox("Closed", uiState.closedOutlets.toString())
                            MetricBox("Inactive", uiState.inactiveOutlets.toString())
                        }
                    }

                    item {
                        Text("Pickup Capacity Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            MetricBox("Slots", uiState.totalSlotsToday.toString())
                            MetricBox("Booked", uiState.totalBookedToday.toString())
                            MetricBox("Remaining", uiState.remainingCapacityToday.toString())
                        }
                    }

                    item {
                        Text("Inventory Pressure", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            MetricBox("Tracked", uiState.totalTrackedItems.toString())
                            MetricBox("Available", uiState.availableItems.toString())
                            MetricBox("Low Stock", uiState.lowStockItems.toString())
                            MetricBox("Out of Stock", uiState.outOfStockItems.toString())
                        }
                    }

                    if (uiState.recentActiveOrders.isNotEmpty()) {
                        item {
                            Text("Recent Active Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                        }
                        items(uiState.recentActiveOrders) { order ->
                            OrderItem(order)
                        }
                    }
                    
                    if (uiState.outletList.isNotEmpty()) {
                        item {
                            Text("Outlet Snapshot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                        }
                        items(uiState.outletList) { outlet ->
                            OutletItem(outlet)
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                            Text("Back to Dashboard")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.MetricBox(label: String, value: String) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OrderItem(order: Order) {
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.US)
    val timeStr = timeFormatter.format(Date(order.createdAt))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("#${order.id.takeLast(6).uppercase()}", fontWeight = FontWeight.Bold)
                Text(order.status.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(timeStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OutletItem(outlet: Outlet) {
    val statusText = if (!outlet.isActive) "INACTIVE" else if (outlet.isOpen) "OPEN" else "CLOSED"
    val color = if (!outlet.isActive) Color.Gray else if (outlet.isOpen) Color(0xFF4CAF50) else Color(0xFFF44336)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(outlet.name, fontWeight = FontWeight.SemiBold)
            Text(statusText, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}
