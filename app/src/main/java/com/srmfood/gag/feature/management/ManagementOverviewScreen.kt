package com.srmfood.gag.feature.management

import com.srmfood.gag.core.ui.theme.OrbitLimeDark

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.srmfood.gag.core.ui.component.*
import com.srmfood.gag.core.ui.theme.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.theme.OrbitLime
import com.srmfood.gag.core.ui.theme.OrbitOnLime
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ManagementOverviewScreen(
    viewModel: ManagementViewModel = hiltViewModel()
) {
    val statsState by viewModel.stats.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val currencyFormatter = NumberFormat.getInstance(Locale("en", "IN"))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CraveEyebrow("Management workspace", color = OrbitLimeDark)
                    Text(
                        text = "Campus overview",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 32.sp, lineHeight = 32.sp)
                    )
                }
                
                // Refresh mechanism
                IconButton(
                    onClick = { viewModel.refresh() },
                    enabled = !isRefreshing
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            color = OrbitLimeDark,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh data",
                            tint = OrbitLimeDark
                        )
                    }
                }
            }
        }

        when (val state = statsState) {
            is UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OrbitLimeDark)
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Operations Data Unavailable",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        GagPrimaryButton(text = "Retry", onClick = { viewModel.refresh() }, modifier = Modifier.width(160.dp))
                    }
                }
            }
            is UiState.Success -> {
                val stats = state.data
                
                // TODAY SECTION
                item {
                    SectionTitle("Today")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ManagementStatBox(
                            "Revenue Today",
                            "₹${currencyFormatter.format(stats.revenueToday)}",
                            Modifier.weight(1f),
                            highlight = true
                        )
                        ManagementStatBox(
                            "Orders Today",
                            stats.ordersToday.toString(),
                            Modifier.weight(1f),
                            highlight = true
                        )
                    }
                }

                // SYSTEM TOTALS
                item {
                    SectionTitle("System totals")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ManagementStatBox("Total Revenue", "₹${currencyFormatter.format(stats.revenue)}", Modifier.weight(1f))
                        ManagementStatBox("Total Orders", stats.totalOrders.toString(), Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ManagementStatBox("Users", stats.totalUsers.toString(), Modifier.weight(1f))
                        ManagementStatBox("Vendors", stats.totalVendors.toString(), Modifier.weight(1f))
                        ManagementStatBox("Outlets", stats.totalOutlets.toString(), Modifier.weight(1f))
                    }
                }

                // ORDER STATUS
                item {
                    SectionTitle("Order status")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ManagementStatBox("Active orders", stats.activeOrders.toString(), Modifier.weight(1f))
                        ManagementStatBox("Completed", stats.completedOrders.toString(), Modifier.weight(1f))
                    }
                }
            }
            else -> {}
        }
        
        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    CraveEyebrow(title, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun ManagementStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    val tone = if (highlight) CraveChartreuse else MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(tone)
            .border(1.dp, if (highlight) CraveInk else MaterialTheme.colorScheme.outline, RoundedCornerShape(15.dp))
            .padding(16.dp)
    ) {
        Column {
            CraveMono(value, fontSize = 24f, color = CraveInk.takeIf { highlight } ?: MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = label, color = if (highlight) CraveInk else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
}
