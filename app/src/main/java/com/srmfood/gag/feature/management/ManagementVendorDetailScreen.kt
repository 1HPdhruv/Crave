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
import com.srmfood.gag.domain.model.Outlet
import com.srmfood.gag.domain.model.UserRole
import com.srmfood.gag.domain.model.admin.VendorProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementVendorDetailScreen(
    onBack: () -> Unit,
    viewModel: ManagementVendorDetailViewModel = hiltViewModel()
) {
    val vendorState by viewModel.vendorState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vendor Detail", fontWeight = FontWeight.Bold) },
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
            when (val state = vendorState) {
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
                    val vendor = state.data
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        item {
                            VendorHeaderSection(vendor)
                        }
                        item {
                            OwnerSection(vendor)
                        }
                        if (vendor.outlets.isNotEmpty()) {
                            item {
                                SectionTitle("OUTLETS (${vendor.outlets.size})")
                            }
                            items(vendor.outlets, key = { it.id }) { outlet ->
                                OutletCard(outlet)
                            }
                        } else {
                            item {
                                SectionTitle("OUTLETS (0)")
                                Text("This vendor has no registered outlets yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun VendorHeaderSection(vendor: VendorProfile) {
    SectionTitle("VENDOR IDENTITY")
    BaseCard {
        val statusText = when {
            !vendor.isActive -> "SUSPENDED"
            vendor.role == UserRole.PENDING_VENDOR -> "PENDING"
            else -> "ACTIVE"
        }
        val statusColor = when (statusText) {
            "ACTIVE" -> OrbitLime
            "SUSPENDED" -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(vendor.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = statusText,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        InfoRow("Vendor ID", vendor.id)
        InfoRow("Joined", vendor.createdAt.take(10))
    }
}

@Composable
private fun OwnerSection(vendor: VendorProfile) {
    SectionTitle("OWNER INFORMATION")
    BaseCard {
        InfoRow("Name", vendor.name)
        InfoRow("Email", vendor.email)
        InfoRow("Phone", vendor.phone ?: "Not provided")
    }
}

@Composable
private fun OutletCard(outlet: Outlet) {
    BaseCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(outlet.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = if (outlet.isActive) "ACTIVE" else "INACTIVE",
                color = if (outlet.isActive) OrbitLime else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        InfoRow("Outlet ID", outlet.id)
        InfoRow("Status", if (outlet.isOpen) "Open" else "Closed")
        InfoRow("Hours", "${outlet.operatingHours.openTime} - ${outlet.operatingHours.closeTime}")
        if (outlet.location.building.isNotBlank()) {
            InfoRow("Location", "${outlet.location.building}, Floor ${outlet.location.floor}")
        }
    }
}
