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
import com.srmfood.gag.domain.usecase.admin.TrendPoint
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
                        
                        // Analytics Trend Section
                        AnalyticsTrendSection(data = data, period = selectedPeriod)
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

@Composable
private fun AnalyticsTrendSection(
    data: ManagementAnalytics,
    period: String
) {
    val trendPoints = when (period) {
        "TODAY" -> data.trendToday
        "THIS WEEK" -> data.trendWeek
        "THIS MONTH" -> data.trendMonth
        else -> emptyList()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "ANALYTICS TREND",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Orders and revenue over time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (trendPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No trend data available yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LegendItem(color = OrbitLime, label = "Orders")
                    LegendItem(color = Color(0xFF4CAF50), label = "Revenue")
                }

                Spacer(modifier = Modifier.height(4.dp))

                val maxOrders = (trendPoints.maxOfOrNull { it.orders } ?: 1).coerceAtLeast(1)
                val maxRevenue = (trendPoints.maxOfOrNull { it.revenue } ?: 1.0).coerceAtLeast(1.0)

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TrendSubChart(
                        title = "Orders Trend",
                        points = trendPoints,
                        maxValue = maxOrders.toDouble(),
                        lineColor = OrbitLime,
                        valueFormatter = { it.toInt().toString() }
                    )

                    TrendSubChart(
                        title = "Revenue Trend",
                        points = trendPoints,
                        maxValue = maxRevenue,
                        lineColor = Color(0xFF4CAF50),
                        valueFormatter = { formatCurrency(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TrendSubChart(
    title: String,
    points: List<TrendPoint>,
    maxValue: Double,
    lineColor: Color,
    valueFormatter: (Double) -> String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            val latestValue = points.lastOrNull()?.let { if (title.contains("Orders")) it.orders.toDouble() else it.revenue } ?: 0.0
            Text(
                text = "Latest: ${valueFormatter(latestValue)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(8.dp)
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                if (points.isEmpty()) return@Canvas
                val width = size.width
                val height = size.height
                val stepX = if (points.size > 1) width / (points.size - 1) else width

                val path = androidx.compose.ui.graphics.Path()
                val pointsList = mutableListOf<androidx.compose.ui.geometry.Offset>()

                points.forEachIndexed { index, point ->
                    val value = if (title.contains("Orders")) point.orders.toDouble() else point.revenue
                    val x = index * stepX
                    val ratio = (value / maxValue).coerceIn(0.0, 1.0)
                    val y = height - (ratio * height).toFloat()
                    pointsList.add(androidx.compose.ui.geometry.Offset(x, y))

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = lineColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )

                pointsList.forEach { offset ->
                    drawCircle(
                        color = lineColor,
                        radius = 4.dp.toPx(),
                        center = offset
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = offset
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = points.firstOrNull()?.label ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (points.size > 2) {
                Text(
                    text = points[points.size / 2].label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = points.lastOrNull()?.label ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
