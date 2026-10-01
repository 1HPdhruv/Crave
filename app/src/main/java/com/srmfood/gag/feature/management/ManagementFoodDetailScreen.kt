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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.theme.OrbitLime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementFoodDetailScreen(
    onBack: () -> Unit,
    viewModel: ManagementFoodDetailViewModel = hiltViewModel()
) {
    val detailState by viewModel.detailState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Food Detail", fontWeight = FontWeight.Bold) },
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
                    val foodItem = data.foodItem
                    val outlet = data.outlet

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Image & Identity
                        item {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                if (!foodItem.imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = foodItem.imageUrl,
                                        contentDescription = foodItem.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                
                                Text(foodItem.name, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text("₹${foodItem.price.toInt()}", color = OrbitLime, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                        }

                        // CLASSIFICATION
                        item {
                            SectionTitle("CLASSIFICATION")
                            BaseCard {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                Spacer(modifier = Modifier.height(12.dp))
                                InfoRow("Category", foodItem.category)
                                InfoRow("Prep Time", "${foodItem.prepTimeMinutes} mins")
                                if (foodItem.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(foodItem.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        // OUTLET
                        item {
                            SectionTitle("OUTLET")
                            BaseCard {
                                InfoRow("Outlet Name", foodItem.outletName)
                                InfoRow("Outlet ID", foodItem.outletId)
                                if (outlet != null) {
                                    InfoRow("Vendor ID", outlet.vendorId)
                                    InfoRow("Outlet Status", if (outlet.isActive) "ACTIVE" else "INACTIVE")
                                }
                            }
                        }

                        // CUSTOMIZATIONS
                        item {
                            SectionTitle("CUSTOMIZATIONS")
                            BaseCard {
                                if (foodItem.customizations.isEmpty()) {
                                    Text("No customizations configured.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                } else {
                                    foodItem.customizations.forEachIndexed { index, custom ->
                                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = if (index < foodItem.customizations.lastIndex) 16.dp else 0.dp)) {
                                            Text(custom.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            val req = if (custom.isRequired) "Required" else "Optional"
                                            Text("$req • Max ${custom.maxSelections}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            custom.options.forEach { opt ->
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("• ${opt.name}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                                    Text("+₹${opt.extraPrice.toInt()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // RATINGS
                        item {
                            SectionTitle("RATINGS")
                            BaseCard {
                                if (foodItem.totalReviews > 0) {
                                    InfoRow("Rating", "${foodItem.rating} ★")
                                    InfoRow("Total Reviews", foodItem.totalReviews.toString())
                                } else {
                                    Text("No ratings yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        // INVENTORY
                        item {
                            SectionTitle("INVENTORY")
                            BaseCard {
                                Text("Inventory metrics are not cleanly exposed at the food_item level in the current backend architecture.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
