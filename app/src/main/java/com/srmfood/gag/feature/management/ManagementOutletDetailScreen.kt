package com.srmfood.gag.feature.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.theme.OrbitLime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementOutletDetailScreen(
    onBack: () -> Unit,
    viewModel: ManagementOutletDetailViewModel = hiltViewModel()
) {
    val detailState by viewModel.detailState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Outlet Detail", fontWeight = FontWeight.Bold) },
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
            when (val state = detailState) {
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
                    val data = state.data
                    val outlet = data.outlet
                    val vendor = data.vendor
                    val foodItems = data.foodItems
                    val pickupSlots = data.todayPickupSlots

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Outlet info
                        item {
                            SectionTitle("OUTLET IDENTITY")
                            BaseCard {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(outlet.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (outlet.isActive) {
                                            if (outlet.isOpen) {
                                                StatusBadge("OPEN", OrbitLime)
                                            } else {
                                                StatusBadge("CLOSED", MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            StatusBadge("ACTIVE", OrbitLime)
                                        } else {
                                            StatusBadge("INACTIVE", MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                if (outlet.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(outlet.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                InfoRow("Outlet ID", outlet.id)
                            }
                        }

                        // Vendor
                        item {
                            SectionTitle("VENDOR")
                            BaseCard {
                                if (vendor != null) {
                                    InfoRow("Business/Vendor", vendor.name)
                                    InfoRow("Vendor ID", vendor.id)
                                    InfoRow("Owner Email", vendor.email)
                                } else {
                                    InfoRow("Vendor ID", outlet.vendorId)
                                    Text("Failed to load vendor details.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        // Location & Hours
                        item {
                            SectionTitle("LOCATION & HOURS")
                            BaseCard {
                                val building = outlet.location.building
                                val floor = outlet.location.floor
                                val locDesc = outlet.location.description
                                if (building.isNotBlank()) InfoRow("Building", building)
                                if (floor.isNotBlank()) InfoRow("Floor", floor)
                                if (locDesc.isNotBlank()) InfoRow("Notes", locDesc)
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                InfoRow("Hours", "${outlet.operatingHours.openTime} - ${outlet.operatingHours.closeTime}")
                                if (outlet.operatingHours.daysOpen.isNotEmpty()) {
                                    InfoRow("Days Open", outlet.operatingHours.daysOpen.joinToString(", "))
                                }
                            }
                        }

                        // Menu Summary
                        item {
                            SectionTitle("MENU SUMMARY")
                            BaseCard {
                                if (foodItems != null) {
                                    val active = foodItems.count { it.isAvailable }
                                    val unavailable = foodItems.count { !it.isAvailable }
                                    InfoRow("Active Items", active.toString())
                                    InfoRow("Unavailable Items", unavailable.toString())
                                    InfoRow("Total Catalogue", foodItems.size.toString())
                                    
                                    val categories = foodItems.groupBy { it.category }
                                    if (categories.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("By Category:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                        categories.forEach { (cat, items) ->
                                            InfoRow(cat, items.size.toString())
                                        }
                                    }
                                } else {
                                    Text("Menu summary currently unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        
                        // Order Summary
                        item {
                            SectionTitle("ORDER SUMMARY")
                            BaseCard {
                                Text("Order metrics cannot be efficiently aggregated by outlet in the current architecture without risking N+1 queries or heavy data loads.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // Pickup Summary
                        item {
                            SectionTitle("TODAY'S PICKUP OPERATIONS")
                            BaseCard {
                                if (pickupSlots != null) {
                                    if (pickupSlots.isEmpty()) {
                                        Text("No pickup slots scheduled for today.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    } else {
                                        val totalCapacity = pickupSlots.sumOf { it.capacity }
                                        val totalBooked = pickupSlots.sumOf { it.bookedCount }
                                        InfoRow("Total Slots Today", pickupSlots.size.toString())
                                        InfoRow("Booked Capacity", "$totalBooked / $totalCapacity")
                                    }
                                } else {
                                    Text("Pickup slot summary currently unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        
                        // Inventory Summary
                        item {
                            SectionTitle("INVENTORY SUMMARY")
                            BaseCard {
                                Text("Inventory metrics are not cleanly exposed at the outlet level in the current backend architecture.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
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
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
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
private fun StatusBadge(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
