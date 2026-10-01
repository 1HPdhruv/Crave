package com.srmfood.gag.feature.management

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.theme.OrbitLime
import com.srmfood.gag.domain.usecase.admin.ManagementAnalytics
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementAnalyticsScreen(
    viewModel: ManagementAnalyticsViewModel = hiltViewModel()
) {
    val analyticsState by viewModel.analyticsState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    
    // UI state for period selection
    var selectedPeriod by remember { mutableStateOf("TODAY") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Analytics",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Real-time command center",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(
                onClick = { viewModel.refresh() },
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = OrbitLime, strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh", tint = OrbitLime)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = analyticsState) {
                is UiState.Loading -> {
                    CircularProgressIndicator(
                        color = OrbitLime,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Error", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
                is UiState.Success -> {
                    val data = state.data
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Period Selector
                        PeriodSelector(
                            selectedPeriod = selectedPeriod,
                            onPeriodSelected = { selectedPeriod = it }
                        )

                        // Highlight Metrics
                        HighlightMetrics(data = data, period = selectedPeriod)

                        // Orders
                        MetricSection("ORDERS") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricCard(title = "Total", value = data.totalOrders.toString(), modifier = Modifier.weight(1f))
                                MetricCard(title = "Completed", value = data.completedOrders.toString(), modifier = Modifier.weight(1f), highlightColor = Color(0xFF4CAF50))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricCard(title = "Active", value = data.activeOrders.toString(), modifier = Modifier.weight(1f), highlightColor = OrbitLime)
                                MetricCard(title = "Cancelled/Rejected", value = (data.cancelledOrders + data.rejectedOrders).toString(), modifier = Modifier.weight(1f), highlightColor = MaterialTheme.colorScheme.error)
                            }
                        }

                        // Revenue Breakdown
                        MetricSection("REVENUE") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricCard(title = "Paid Revenue", value = formatCurrency(data.paidRevenue), modifier = Modifier.weight(1f), highlightColor = Color(0xFF4CAF50))
                                MetricCard(title = "Refunded", value = formatCurrency(data.refundedAmount), modifier = Modifier.weight(1f), highlightColor = MaterialTheme.colorScheme.error)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricCard(title = "Razorpay", value = formatCurrency(data.razorpayRevenue), modifier = Modifier.weight(1f))
                                MetricCard(title = "Cash", value = formatCurrency(data.cashRevenue), modifier = Modifier.weight(1f))
                            }
                        }

                        // Platform
                        MetricSection("PLATFORM") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricCard(title = "Students", value = data.totalStudents.toString(), modifier = Modifier.weight(1f))
                                MetricCard(title = "Vendors", value = data.totalVendors.toString(), modifier = Modifier.weight(1f))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricCard(title = "Total Outlets", value = data.totalOutlets.toString(), modifier = Modifier.weight(1f))
                                MetricCard(title = "Active Outlets", value = data.activeOutlets.toString(), modifier = Modifier.weight(1f), highlightColor = OrbitLime)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricCard(title = "Total Food", value = data.totalFoodItems.toString(), modifier = Modifier.weight(1f))
                                MetricCard(title = "Available Food", value = data.availableFoodItems.toString(), modifier = Modifier.weight(1f), highlightColor = OrbitLime)
                            }
                        }
                        
                        // Deferred notices
                        Text(
                            text = "Historical trends, top-selling items, and outlet performance metrics are deferred. The backend currently lacks optimized time-series or complex aggregate analytics APIs for these dimensions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit
) {
    val periods = listOf("TODAY", "THIS WEEK", "THIS MONTH")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        periods.forEach { period ->
            val isSelected = selectedPeriod == period
            val bgColor = if (isSelected) OrbitLime else Color.Transparent
            val textColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .clickable { onPeriodSelected(period) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = period,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun HighlightMetrics(data: ManagementAnalytics, period: String) {
    val (orders, revenue) = when (period) {
        "TODAY" -> data.ordersToday to data.revenueToday
        "THIS WEEK" -> data.ordersThisWeek to data.revenueThisWeek
        "THIS MONTH" -> data.ordersThisMonth to data.revenueThisMonth
        else -> 0 to 0.0
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HighlightCard(
            title = "Orders ($period)",
            value = orders.toString(),
            modifier = Modifier.weight(1f)
        )
        HighlightCard(
            title = "Revenue ($period)",
            value = formatCurrency(revenue),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HighlightCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MetricSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        content()
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    highlightColor: Color? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = highlightColor ?: MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.currency = Currency.getInstance("INR")
    format.maximumFractionDigits = 0
    return format.format(amount)
}
