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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.theme.OrbitLime
import com.srmfood.gag.core.ui.theme.OrbitOnLime
import com.srmfood.gag.domain.model.FoodItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementMenuScreen(
    onNavigateToFoodDetail: (String) -> Unit,
    viewModel: ManagementMenuViewModel = hiltViewModel()
) {
    val menuState by viewModel.menuState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    val availabilityFilter by viewModel.availabilityFilter.collectAsState()
    val outletFilter by viewModel.outletFilter.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val vegFilter by viewModel.vegFilter.collectAsState()
    
    val outlets by viewModel.outletsState.collectAsState()
    val categories by viewModel.categoriesState.collectAsState()

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
                    text = "Menu",
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
            placeholder = { Text("Search food...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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

        // Advanced Filters
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Availability Filter
            item {
                var expanded by remember { mutableStateOf(false) }
                val options = listOf("ALL" to "All Status", "AVAILABLE" to "Available", "UNAVAILABLE" to "Unavailable")
                val currentText = options.find { it.first == availabilityFilter }?.second ?: "Status"
                
                FilterChipWithDropdown(
                    text = currentText,
                    isActive = availabilityFilter != "ALL",
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    options.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.updateAvailabilityFilter(key)
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Outlet Filter
            item {
                var expanded by remember { mutableStateOf(false) }
                val currentText = if (outletFilter == "ALL") "All Outlets" else outlets.find { it.id == outletFilter }?.name ?: "Unknown Outlet"
                
                FilterChipWithDropdown(
                    text = currentText,
                    isActive = outletFilter != "ALL",
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    DropdownMenuItem(text = { Text("All Outlets") }, onClick = { viewModel.updateOutletFilter("ALL"); expanded = false })
                    outlets.forEach { outlet ->
                        DropdownMenuItem(
                            text = { Text(outlet.name) },
                            onClick = { viewModel.updateOutletFilter(outlet.id); expanded = false }
                        )
                    }
                }
            }
            
            // Category Filter
            item {
                var expanded by remember { mutableStateOf(false) }
                val currentText = if (categoryFilter == "ALL") "All Categories" else categoryFilter
                
                FilterChipWithDropdown(
                    text = currentText,
                    isActive = categoryFilter != "ALL",
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    DropdownMenuItem(text = { Text("All Categories") }, onClick = { viewModel.updateCategoryFilter("ALL"); expanded = false })
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = { viewModel.updateCategoryFilter(cat.name); expanded = false }
                        )
                    }
                }
            }

            // Veg Filter
            item {
                var expanded by remember { mutableStateOf(false) }
                val options = listOf("ALL" to "Any Diet", "VEG" to "Veg Only", "NON_VEG" to "Non-Veg Only")
                val currentText = options.find { it.first == vegFilter }?.second ?: "Diet"
                
                FilterChipWithDropdown(
                    text = currentText,
                    isActive = vegFilter != "ALL",
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    options.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.updateVegFilter(key)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Menu List
        when (val state = menuState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OrbitLime)
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Error fetching menu", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { viewModel.refresh() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Text("Retry", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            is UiState.Success -> {
                val foodItems = state.data
                if (foodItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No food items match the current filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    // Quick category summary header
                    val summary = foodItems.groupBy { it.category }.map { "${it.key} · ${it.value.size}" }.joinToString("   |   ")
                    if (summary.isNotBlank()) {
                        Text(
                            text = summary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(foodItems, key = { it.id }) { item ->
                            ManagementFoodCard(
                                foodItem = item,
                                onClick = { onNavigateToFoodDetail(item.id) }
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun FilterChipWithDropdown(
    text: String,
    isActive: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    dropdownContent: @Composable ColumnScope.() -> Unit
) {
    Box {
        FilterChip(
            selected = isActive,
            onClick = { onExpandedChange(true) },
            label = { Text(text, fontWeight = FontWeight.Bold) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = OrbitLime,
                selectedLabelColor = OrbitOnLime,
                selectedTrailingIconColor = OrbitOnLime,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            border = null,
            shape = RoundedCornerShape(16.dp)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            content = dropdownContent
        )
    }
}

@Composable
private fun ManagementFoodCard(
    foodItem: FoodItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Image
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (!foodItem.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = foodItem.imageUrl,
                        contentDescription = foodItem.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text(
                        text = foodItem.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "₹${foodItem.price.toInt()}",
                        color = OrbitLime,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                
                Text(
                    text = "${foodItem.outletName} • ${foodItem.category}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (foodItem.isVeg) {
                        StatusBadge("VEG", Color(0xFF4CAF50))
                    } else {
                        StatusBadge("NON-VEG", Color(0xFFF44336))
                    }

                    if (foodItem.isAvailable) {
                        StatusBadge("AVAILABLE", OrbitLime)
                    } else {
                        StatusBadge("UNAVAILABLE", MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
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
