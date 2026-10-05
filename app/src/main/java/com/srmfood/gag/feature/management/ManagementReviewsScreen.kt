package com.srmfood.gag.feature.management

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.FoodReview
import com.srmfood.gag.domain.model.OutletReview
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.core.ui.component.GagLoadingScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementReviewsScreen(
    onBack: () -> Unit,
    viewModel: ManagementReviewsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reviews", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val currentState = if (uiState.selectedTab == ManagementReviewTab.FOOD) uiState.reviewsState else uiState.outletReviewsState
            
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = uiState.selectedTab.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
                    Tab(
                        selected = uiState.selectedTab == ManagementReviewTab.FOOD,
                        onClick = { viewModel.setTab(ManagementReviewTab.FOOD) },
                        text = { Text("Food") }
                    )
                    Tab(
                        selected = uiState.selectedTab == ManagementReviewTab.OUTLET,
                        onClick = { viewModel.setTab(ManagementReviewTab.OUTLET) },
                        text = { Text("Outlet") }
                    )
                }
                
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (currentState) {
                is UiState.Loading -> GagLoadingScreen()
                is UiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Management reviews are temporarily unavailable.", color = GagError)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = viewModel::loadReviews,
                            colors = ButtonDefaults.buttonColors(containerColor = GagOrange)
                        ) {
                            Text("Retry")
                        }
                    }
                }
                is UiState.Empty -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (uiState.searchQuery.isNotBlank() || uiState.ratingFilter != null) {
                            Text("No reviews match your filters.", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { 
                                viewModel.updateSearchQuery("")
                                viewModel.updateRatingFilter(null)
                            }) {
                                Text("Clear Filters")
                            }
                        } else {
                            Text("No reviews yet.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is UiState.Success -> {
                    val reviews = (currentState as UiState.Success).data
                    
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            // Summary Metrics
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Star, contentDescription = null, tint = GagYellow, modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(String.format("%.1f", uiState.averageRating), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        }
                                        Text("${uiState.totalReviews} total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MetricColumn("5★", uiState.total5Star)
                                        MetricColumn("4★", uiState.total4Star)
                                        MetricColumn("3★", uiState.total3Star)
                                        MetricColumn("2★", uiState.total2Star)
                                        MetricColumn("1★", uiState.total1Star)
                                    }
                                }
                            }
                        }
                        
                        item {
                            // Search
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::updateSearchQuery,
                                placeholder = { Text("Search food, outlet, review...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        
                        item {
                            // Filter row
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = uiState.ratingFilter == null,
                                        onClick = { viewModel.updateRatingFilter(null) },
                                        label = { Text("All") }
                                    )
                                }
                                items(listOf(5, 4, 3, 2, 1)) { rating ->
                                    FilterChip(
                                        selected = uiState.ratingFilter == rating,
                                        onClick = { viewModel.updateRatingFilter(rating) },
                                        label = { Text("$rating★") }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider()
                        }
                        
                        if (uiState.selectedTab == ManagementReviewTab.FOOD) {
                            items((reviews as List<FoodReview>), key = { it.id }) { review ->
                                ManagementReviewCard(
                                    review = review,
                                    isToggling = uiState.isTogglingVisibilityFor == review.id,
                                    onToggleVisibility = { viewModel.toggleVisibility(review.id, review.isVisible) }
                                )
                            }
                        } else {
                            items((reviews as List<OutletReview>), key = { it.id }) { review ->
                                ManagementOutletReviewCard(
                                    review = review,
                                    isToggling = uiState.isTogglingVisibilityFor == review.id,
                                    onToggleVisibility = { viewModel.toggleVisibility(review.id, review.isVisible) }
                                )
                            }
                        }
                    }
                }
                is UiState.Idle -> {}
            }
                }
        }
        }
    }
}

@Composable
fun MetricColumn(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ManagementReviewCard(
    review: FoodReview,
    isToggling: Boolean,
    onToggleVisibility: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (review.rating <= 2) GagError.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp, 
            if (review.rating <= 2) GagError.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) { i ->
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (i < review.rating) GagYellow else Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(review.foodName ?: "Food Item", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("by ${review.studentName.ifBlank { "Student" }} • ${review.createdAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (review.isVisible) GagSuccess.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).clickable(enabled = !isToggling, onClick = onToggleVisibility),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isToggling) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                if (review.isVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                contentDescription = "Visibility",
                                modifier = Modifier.size(12.dp),
                                tint = if (review.isVisible) GagSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (review.isVisible) "Visible" else "Hidden",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (review.isVisible) GagSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            if (!review.reviewText.isNullOrBlank()) {
                Text("\"${review.reviewText}\"", style = MaterialTheme.typography.bodyMedium)
            }
            
            if (review.vendorReply != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Vendor Reply", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GagOrange)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(review.vendorReply.replyText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun ManagementOutletReviewCard(
    review: OutletReview,
    isToggling: Boolean,
    onToggleVisibility: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (review.rating <= 2) GagError.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp, 
            if (review.rating <= 2) GagError.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) { i ->
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (i < review.rating) GagYellow else Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(review.outletName ?: "Outlet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("by ${review.studentName.ifBlank { "Student" }} • ${review.createdAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (review.isVisible) GagSuccess.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).clickable(enabled = !isToggling, onClick = onToggleVisibility),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isToggling) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                if (review.isVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                contentDescription = "Visibility",
                                modifier = Modifier.size(12.dp),
                                tint = if (review.isVisible) GagSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (review.isVisible) "Visible" else "Hidden",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (review.isVisible) GagSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            if (!review.reviewText.isNullOrBlank()) {
                Text("\"${review.reviewText}\"", style = MaterialTheme.typography.bodyMedium)
            }
            
            if (review.vendorReply != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Vendor Reply", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GagOrange)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(review.vendorReply.replyText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
