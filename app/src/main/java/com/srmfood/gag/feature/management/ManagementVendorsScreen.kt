package com.srmfood.gag.feature.management

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.theme.OrbitLime
import com.srmfood.gag.core.ui.theme.OrbitOnLime
import com.srmfood.gag.domain.model.UserRole
import com.srmfood.gag.domain.model.admin.VendorProfile

@Composable
fun ManagementVendorsScreen(
    onNavigateToVendorDetail: (String) -> Unit,
    viewModel: ManagementVendorsViewModel = hiltViewModel()
) {
    val vendorsState by viewModel.vendorsState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

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
                    text = "CRAVE | MANAGEMENT",
                    color = OrbitLime,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Vendors",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = { viewModel.refresh() },
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(color = OrbitLime, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh", tint = OrbitLime)
                }
            }
        }

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Search vendors...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = OrbitLime) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        viewModel.updateSearchQuery("")
                        viewModel.submitSearch()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = OrbitLime,
                unfocusedBorderColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.submitSearch() })
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Filters
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("ALL", "VENDOR", "PENDING_VENDOR", "SUSPENDED")
            val displayNames = listOf("All", "Active", "Pending", "Suspended")
            
            items(filters.size) { index ->
                val filter = filters[index]
                val name = displayNames[index]
                FilterChip(
                    selected = currentFilter == filter,
                    onClick = { viewModel.updateFilter(filter) },
                    label = { Text(name, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OrbitLime,
                        selectedLabelColor = OrbitOnLime,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = null,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // List
        when (val state = vendorsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OrbitLime)
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Error fetching vendors", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { viewModel.refresh() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Text("Retry", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            is UiState.Success -> {
                val vendors = state.data
                if (vendors.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No vendors found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val pending = vendors.filter { it.role == UserRole.PENDING_VENDOR }
                    val activeOrSuspended = vendors.filter { it.role != UserRole.PENDING_VENDOR }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (pending.isNotEmpty()) {
                            item {
                                Text(
                                    text = "PENDING APPLICATIONS",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            items(pending, key = { it.id }) { vendor ->
                                ManagementVendorCard(
                                    vendor = vendor,
                                    onClick = { onNavigateToVendorDetail(vendor.id) },
                                    isPending = true
                                )
                            }
                            item { Spacer(modifier = Modifier.height(8.dp)) }
                        }

                        if (activeOrSuspended.isNotEmpty()) {
                            if (pending.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "VENDORS",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                            items(activeOrSuspended, key = { it.id }) { vendor ->
                                ManagementVendorCard(
                                    vendor = vendor,
                                    onClick = { onNavigateToVendorDetail(vendor.id) },
                                    isPending = false
                                )
                            }
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun ManagementVendorCard(
    vendor: VendorProfile,
    onClick: () -> Unit,
    isPending: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isPending) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = vendor.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
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
                
                Text(
                    text = statusText,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            
            Text(
                text = "${vendor.outlets.size} outlets",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isPending) "Pending since ${vendor.createdAt.take(10)}" else "Joined ${vendor.createdAt.take(10)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                
                if (isPending) {
                    Text(
                        text = "View Application",
                        color = OrbitLime,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
