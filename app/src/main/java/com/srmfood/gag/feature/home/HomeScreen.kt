package com.srmfood.gag.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.*
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.FoodCategory
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.OrderStatus
import com.srmfood.gag.domain.model.Outlet
import java.util.Calendar

@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onOutletClick: (String) -> Unit,
    onFoodClick: (String) -> Unit,
    onCartClick: () -> Unit,
    onNavigateBottom: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val greeting = getGreeting(uiState.user?.name?.split(" ")?.firstOrNull() ?: "Student")

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            GagBottomNavBar(
                items = studentBottomNavItems,
                currentRoute = "home",
                onItemSelected = onNavigateBottom
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = GagSpacing.ExtraLarge)
        ) {
            // ─── Header ──────────────────────────────────────────
            item {
                HomeTopHeader(
                    greeting = greeting,
                    userName = uiState.user?.name ?: "Loading...",
                    cartCount = uiState.cartItemCount,
                    onCartClick = onCartClick,
                    onNotificationsClick = { onNavigateBottom("alerts") }
                )
            }

            // ─── Search Bar ──────────────────────────────────────
            item {
                GagSearchBar(
                    query = "",
                    onQueryChange = {},
                    modifier = Modifier
                        .padding(horizontal = GagSpacing.Large)
                        .clickable { onSearchClick() }
                )
                Spacer(modifier = Modifier.height(GagSpacing.Large))
            }

            // ─── Promotional Banner ──────────────────────────────
            item {
                PromoBanner(
                    modifier = Modifier
                        .padding(horizontal = GagSpacing.Large)
                        .padding(bottom = GagSpacing.Large)
                )
            }

            // ─── Active Order Card ────────────────────────────────
            uiState.activeOrder?.let { order ->
                item {
                    ActiveOrderCard(
                        order = order,
                        onClick = { onNavigateBottom("orders") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = GagSpacing.Large)
                            .padding(bottom = GagSpacing.Large)
                    )
                }
            }

            // ─── Categories ───────────────────────────────────────
            item {
                GagSectionHeader(
                    title = "What are you craving?",
                    modifier = Modifier.padding(horizontal = GagSpacing.Large)
                )
                Spacer(modifier = Modifier.height(GagSpacing.Medium))
                
                when (val catState = uiState.categories) {
                    is UiState.Success -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                            horizontalArrangement = Arrangement.spacedBy(GagSpacing.Medium)
                        ) {
                            item {
                                GagCategoryChip(
                                    label = "All",
                                    isSelected = true,
                                    onClick = { /* Already on All */ },
                                    emoji = null
                                )
                            }
                            items(catState.data) { category ->
                                GagCategoryChip(
                                    label = category.name,
                                    isSelected = false,
                                    onClick = { onCategoryClick(category.name) },
                                    iconUrl = category.imageUrl,
                                    emoji = category.emoji
                                )
                            }
                        }
                    }
                    is UiState.Loading -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                            horizontalArrangement = Arrangement.spacedBy(GagSpacing.Medium)
                        ) {
                            items(6) { GagLoadingSkeleton(modifier = Modifier.size(68.dp)) }
                        }
                    }
                    is UiState.Error -> {
                        GagErrorState(
                            message = catState.message,
                            onRetry = { viewModel.loadData() },
                            modifier = Modifier.padding(horizontal = GagSpacing.Large)
                        )
                    }
                    else -> {}
                }
                Spacer(modifier = Modifier.height(GagSpacing.ExtraLarge))
            }

            // ─── Popular Foods ────────────────────────────────────
            item {
                GagSectionHeader(
                    title = "Popular right now",
                    modifier = Modifier.padding(horizontal = GagSpacing.Large)
                )
                Spacer(modifier = Modifier.height(GagSpacing.Medium))
            }
            when (val popularState = uiState.popularFood) {
                is UiState.Success -> {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                            horizontalArrangement = Arrangement.spacedBy(GagSpacing.Medium)
                        ) {
                            items(popularState.data.take(5)) { food ->
                                FoodItemCard(
                                    foodItem = food,
                                    onClick = { onFoodClick(food.id) },
                                    onAddToCart = {
                                        if (food.customizations.isNotEmpty()) {
                                            onFoodClick(food.id)
                                        } else {
                                            viewModel.addToCart(food)
                                        }
                                    },
                                    modifier = Modifier.width(220.dp)
                                )
                            }
                        }
                    }
                    if (popularState.data.isEmpty()) {
                        item {
                            GagEmptyState(
                                title = "No popular food",
                                description = "Check back later for trending items.",
                                modifier = Modifier.padding(horizontal = GagSpacing.Large)
                            )
                        }
                    }
                }
                is UiState.Loading -> {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                            horizontalArrangement = Arrangement.spacedBy(GagSpacing.Medium)
                        ) {
                            items(3) {
                                GagLoadingSkeleton(
                                    modifier = Modifier.size(220.dp, 260.dp)
                                )
                            }
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        GagErrorState(
                            message = popularState.message,
                            onRetry = { viewModel.loadData() },
                            modifier = Modifier.padding(horizontal = GagSpacing.Large)
                        )
                    }
                }
                else -> {}
            }
            item { Spacer(modifier = Modifier.height(GagSpacing.Medium)) }

            // ─── Nearby Outlets ───────────────────────────────────
            item {
                GagSectionHeader(
                    title = "Explore Outlets",
                    actionText = "See All",
                    onActionClick = { onNavigateBottom("outlets") },
                    modifier = Modifier.padding(horizontal = GagSpacing.Large)
                )
                Spacer(modifier = Modifier.height(GagSpacing.Medium))
                
                when (val outletState = uiState.outlets) {
                    is UiState.Success -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                            horizontalArrangement = Arrangement.spacedBy(GagSpacing.Medium)
                        ) {
                            items(outletState.data.take(4)) { outlet ->
                                OutletCard(outlet = outlet, onClick = { onOutletClick(outlet.id) })
                            }
                        }
                    }
                    is UiState.Loading -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                            horizontalArrangement = Arrangement.spacedBy(GagSpacing.Medium)
                        ) {
                            items(3) { GagLoadingSkeleton(modifier = Modifier.size(220.dp, 180.dp)) }
                        }
                    }
                    is UiState.Error -> {
                        GagErrorState(
                            message = outletState.message,
                            onRetry = { viewModel.loadData() },
                            modifier = Modifier.padding(horizontal = GagSpacing.Large)
                        )
                    }
                    else -> {}
                }
                Spacer(modifier = Modifier.height(GagSpacing.ExtraLarge))
            }

            // ─── Recommended Foods ─────────────────────────────────────
            item {
                GagSectionHeader(
                    title = "Recommended for you",
                    modifier = Modifier.padding(horizontal = GagSpacing.Large)
                )
                Spacer(modifier = Modifier.height(GagSpacing.Medium))
            }
            when (val recState = uiState.recommendedFood) {
                is UiState.Success -> {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                            horizontalArrangement = Arrangement.spacedBy(GagSpacing.Medium)
                        ) {
                            items(recState.data.take(4)) { food ->
                                FoodItemCard(
                                    foodItem = food,
                                    onClick = { onFoodClick(food.id) },
                                    onAddToCart = {
                                        if (food.customizations.isNotEmpty()) {
                                            onFoodClick(food.id)
                                        } else {
                                            viewModel.addToCart(food)
                                        }
                                    },
                                    modifier = Modifier.width(220.dp)
                                )
                            }
                        }
                    }
                    if (recState.data.isEmpty()) {
                        item {
                            GagEmptyState(
                                title = "Nothing recommended yet",
                                description = "Order more food to get personalized recommendations.",
                                modifier = Modifier.padding(horizontal = GagSpacing.Large)
                            )
                        }
                    }
                }
                is UiState.Loading -> {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                            horizontalArrangement = Arrangement.spacedBy(GagSpacing.Medium)
                        ) {
                            items(3) {
                                GagLoadingSkeleton(
                                    modifier = Modifier.size(220.dp, 260.dp)
                                )
                            }
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        GagErrorState(
                            message = recState.message,
                            onRetry = { viewModel.loadData() },
                            modifier = Modifier.padding(horizontal = GagSpacing.Large)
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

// ─── Sub-Components ───────────────────────────────────────────────────────────

@Composable
private fun HomeTopHeader(
    greeting: String,
    userName: String,
    cartCount: Int,
    onCartClick: () -> Unit,
    onNotificationsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = GagSpacing.Large, vertical = GagSpacing.Medium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = GagPink, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "SRM KTR Campus",
                    style = MaterialTheme.typography.labelLarge,
                    color = GagPink,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$greeting, ${userName.split(" ").firstOrNull() ?: ""}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GagSpacing.Small)
        ) {
            GagIconButton(
                icon = Icons.Default.Notifications,
                onClick = onNotificationsClick
            )
            CartIconButton(
                cartCount = cartCount,
                onClick = onCartClick
            )
        }
    }
}

@Composable
private fun PromoBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(GagPink, GagBlue)
                )
            )
            .padding(GagSpacing.Large)
    ) {
        Column {
            Text(
                text = "Hungry?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                text = "Your favourites\nare waiting!",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(GagSpacing.Medium))
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.clickable { /* decorative */ }
            ) {
                Text(
                    text = "ORDER NOW",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = GagPink,
                    modifier = Modifier.padding(horizontal = GagSpacing.Medium, vertical = GagSpacing.Small)
                )
            }
        }
    }
}

@Composable
private fun OutletCard(outlet: Outlet, onClick: () -> Unit) {
    GagCard(
        modifier = Modifier
            .width(240.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = outlet.imageUrl,
                    contentDescription = outlet.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                // Open/closed badge
                GagBadge(
                    text = if (outlet.isOpen) "Open" else "Closed",
                    color = Color.White,
                    containerColor = if (outlet.isOpen) GagGreen else GagOrange,
                    modifier = Modifier
                        .padding(GagSpacing.Medium)
                        .align(Alignment.TopStart)
                )
            }
            Column(modifier = Modifier.padding(GagSpacing.Medium)) {
                Text(
                    text = outlet.name, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = outlet.location.building, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(GagSpacing.Small))
                if (outlet.isOpen && outlet.estimatedWaitMinutes > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null, tint = GagOrange, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "~${outlet.estimatedWaitMinutes} min wait", 
                            style = MaterialTheme.typography.labelMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveOrderCard(order: Order, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val statusColor = when (order.status) {
        OrderStatus.PREPARING -> GagBlue
        OrderStatus.READY -> GagGreen
        OrderStatus.ACCEPTED -> GagBlue
        else -> GagPink
    }

    GagCard(modifier = modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(GagSpacing.Large),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Circle, null, tint = statusColor, modifier = Modifier.size(10.dp))
                    Spacer(modifier = Modifier.width(GagSpacing.Small))
                    Text(
                        text = "Active Order · ${order.status.displayName}", 
                        style = MaterialTheme.typography.labelLarge, 
                        color = statusColor, 
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(GagSpacing.Small))
                Text(
                    text = order.orderNumber, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = order.outletName, 
                    style = MaterialTheme.typography.bodyMedium, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            GagIconButton(
                icon = Icons.Default.ArrowForward,
                onClick = onClick,
                containerColor = GagPink.copy(alpha = 0.1f),
                contentColor = GagPink
            )
        }
    }
}

private fun getGreeting(firstName: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    return greet
}
