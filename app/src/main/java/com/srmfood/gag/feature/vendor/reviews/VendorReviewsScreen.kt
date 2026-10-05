package com.srmfood.gag.feature.vendor.reviews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.FoodReview
import com.srmfood.gag.domain.model.OutletReview
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.core.ui.component.GagLoadingScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorReviewsScreen(
    onBack: () -> Unit,
    viewModel: VendorReviewsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirmForFood by remember { mutableStateOf<FoodReview?>(null) }
    var showDeleteConfirmForOutlet by remember { mutableStateOf<OutletReview?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Food Reviews", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val currentState = if (uiState.selectedTab == VendorReviewTab.FOOD) uiState.reviewsState else uiState.outletReviewsState
            
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = uiState.selectedTab.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
                    Tab(
                        selected = uiState.selectedTab == VendorReviewTab.FOOD,
                        onClick = { viewModel.setTab(VendorReviewTab.FOOD) },
                        text = { Text("Food") }
                    )
                    Tab(
                        selected = uiState.selectedTab == VendorReviewTab.OUTLET,
                        onClick = { viewModel.setTab(VendorReviewTab.OUTLET) },
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
                        Text("Unable to load reviews.", color = GagError)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadReviews() },
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
                            Text("No reviews yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Reviews from your customers will appear here after completed orders.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    val reviews = (currentState as UiState.Success).data
                    val avgRating = if (reviews.isEmpty()) 0.0 else if (uiState.selectedTab == VendorReviewTab.FOOD) {
                        (reviews as List<FoodReview>).map { it.rating }.average()
                    } else {
                        (reviews as List<OutletReview>).map { it.rating }.average()
                    }
                    val totalReviews = reviews.size
                    
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            // Summary Card
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = GagYellow.copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Star, contentDescription = null, tint = GagYellow, modifier = Modifier.size(32.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(String.format("%.1f", avgRating), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Text("$totalReviews total reviews", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        
                        item {
                            // Search
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::updateSearchQuery,
                                placeholder = { Text("Search food...") },
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
                        
                        if (uiState.selectedTab == VendorReviewTab.FOOD) {
                            items((reviews as List<FoodReview>), key = { it.id }) { review ->
                                VendorReviewCard(
                                    review = review,
                                    onReply = { viewModel.openReplyComposer(review) },
                                    onDeleteReply = { showDeleteConfirmForFood = review }
                                )
                            }
                        } else {
                            items((reviews as List<OutletReview>), key = { it.id }) { review ->
                                VendorOutletReviewCard(
                                    review = review,
                                    onReply = { viewModel.openOutletReplyComposer(review) },
                                    onDeleteReply = { showDeleteConfirmForOutlet = review }
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

    if (uiState.isReplyingTo != null || uiState.isReplyingToOutletReview != null) {
        val reviewName = uiState.isReplyingTo?.foodName ?: uiState.isReplyingToOutletReview?.outletName ?: "Item"
        val reviewRating = uiState.isReplyingTo?.rating ?: uiState.isReplyingToOutletReview?.rating ?: 0
        val reviewText = uiState.isReplyingTo?.reviewText ?: uiState.isReplyingToOutletReview?.reviewText ?: ""
        Dialog(onDismissRequest = viewModel::closeReplyComposer) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("REPLY TO REVIEW", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(reviewName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(reviewRating) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = GagYellow, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("\"$reviewText\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = uiState.replyText,
                        onValueChange = viewModel::updateReplyText,
                        placeholder = { Text("Write your reply...") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        isError = uiState.replyError != null,
                        supportingText = {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(uiState.replyError ?: "")
                                Text("${uiState.replyText.length} / 1000")
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = viewModel::closeReplyComposer) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = viewModel::submitReply,
                            enabled = uiState.replyText.isNotBlank() && !uiState.isSubmittingReply,
                            colors = ButtonDefaults.buttonColors(containerColor = GagOrange)
                        ) {
                            if (uiState.isSubmittingReply) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                            } else {
                                Text("Reply")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmForFood != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmForFood = null },
            title = { Text("Delete reply?", fontWeight = FontWeight.Bold) },
            text = { Text("This reply will be removed from the review.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteReply(showDeleteConfirmForFood!!)
                        showDeleteConfirmForFood = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GagError)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmForFood = null }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirmForOutlet != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmForOutlet = null },
            title = { Text("Delete reply?", fontWeight = FontWeight.Bold) },
            text = { Text("This reply will be removed from the review.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteOutletReply(showDeleteConfirmForOutlet!!)
                        showDeleteConfirmForOutlet = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GagError)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmForOutlet = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun VendorReviewCard(
    review: FoodReview,
    onReply: () -> Unit,
    onDeleteReply: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
        Spacer(modifier = Modifier.height(4.dp))
        if (!review.reviewText.isNullOrBlank()) {
            Text("\"${review.reviewText}\"", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "${review.studentName.ifBlank { "Student" }} • ${review.createdAt}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (review.vendorReply != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Vendor Reply", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = GagOrange)
                            Icon(Icons.Filled.Star, contentDescription = "Replied", tint = GagOrange, modifier = Modifier.size(12.dp).padding(start = 4.dp))
                        }
                        Row {
                            IconButton(onClick = onReply, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = onDeleteReply, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = GagError)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(review.vendorReply.replyText, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            TextButton(
                onClick = onReply,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Reply")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Divider()
    }
}

@Composable
fun VendorOutletReviewCard(
    review: OutletReview,
    onReply: () -> Unit,
    onDeleteReply: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
        Spacer(modifier = Modifier.height(4.dp))
        if (!review.reviewText.isNullOrBlank()) {
            Text("\"${review.reviewText}\"", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "${review.studentName.ifBlank { "Student" }} • ${review.createdAt}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (review.vendorReply != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Vendor Reply", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = GagOrange)
                            Icon(Icons.Filled.Star, contentDescription = "Replied", tint = GagOrange, modifier = Modifier.size(12.dp).padding(start = 4.dp))
                        }
                        Row {
                            IconButton(onClick = onReply, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = onDeleteReply, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = GagError)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(review.vendorReply.replyText, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            TextButton(
                onClick = onReply,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Reply")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Divider()
    }
}
