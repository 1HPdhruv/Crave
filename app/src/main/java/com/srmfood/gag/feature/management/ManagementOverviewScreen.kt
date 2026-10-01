package com.srmfood.gag.feature.management

import androidx.compose.foundation.background
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "CRAVE | MANAGEMENT",
                        color = OrbitLime,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Overview",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Refresh mechanism
                IconButton(
                    onClick = { viewModel.refresh() },
                    enabled = !isRefreshing
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            color = OrbitLime,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh data",
                            tint = OrbitLime
                        )
                    }
                }
            }
        }

        when (val state = statsState) {
            is UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OrbitLime)
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
                        Button(
                            onClick = { viewModel.refresh() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onErrorContainer)
                        ) {
                            Text("Retry", color = MaterialTheme.colorScheme.errorContainer)
                        }
                    }
                }
            }
            is UiState.Success -> {
                val stats = state.data
                
                // TODAY SECTION
                item {
                    SectionTitle("TODAY")
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
                    SectionTitle("SYSTEM TOTALS")
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
                    SectionTitle("ORDER STATUS")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ManagementStatBox("Active (Preparing)", stats.activeOrders.toString(), Modifier.weight(1f))
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
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp
    )
}

@Composable
private fun ManagementStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlight) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Column {
            Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = if (highlight) OrbitLime else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
