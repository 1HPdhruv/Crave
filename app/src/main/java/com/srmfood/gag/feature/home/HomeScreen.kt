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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.srmfood.gag.domain.repository.HostelAddress
import com.srmfood.gag.domain.repository.OrderingMode
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

            // ─── Delivery / Pickup Selector ──────────────────────────────
            item {
                var showAddressDialog by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .padding(horizontal = GagSpacing.Large)
                        .padding(bottom = GagSpacing.Medium)
                ) {
                    DeliveryModeSelector(
                        selectedMode = uiState.orderingMode,
                        onModeSelected = { viewModel.setOrderingMode(it) }
                    )
                    Spacer(modifier = Modifier.height(GagSpacing.Small))
                    // Delivery mode: hostel address row
                    HostelAddressBanner(
                        address = uiState.hostelAddress,
                        isDelivery = uiState.orderingMode == OrderingMode.DELIVERY,
                        onChangeAddress = { showAddressDialog = true }
                    )
                    // Pickup mode: no outlet known yet on home screen — show generic pickup info
                    if (uiState.orderingMode == OrderingMode.PICKUP) {
                        PickupRestaurantBanner(outletName = "Select a restaurant below")
                    }
                }

                if (showAddressDialog) {
                    HostelAddressDialog(
                        currentAddress = uiState.hostelAddress,
                        onSave = { address ->
                            viewModel.saveHostelAddress(address)
                            showAddressDialog = false
                        },
                        onDismiss = { showAddressDialog = false }
                    )
                }
            }

            // ─── Search Bar ──────────────────────────────────────
            item {
                GagSearchBar(
                    query = "",
                    onQueryChange = {},
                    placeholder = "Search food, drinks, hostels, or outlets...",
                    modifier = Modifier
                        .padding(horizontal = GagSpacing.Large)
                        .clickable { onSearchClick() }
                )
                Spacer(modifier = Modifier.height(GagSpacing.Large))
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

            // ─── Promotional Banner ──────────────────────────────
            item {
                CravePromoBanner(
                    title = "HUNGRY AT SRM?",
                    subtitle = "Hostel delivery & instant campus pickup available",
                    ctaText = "ORDER NOW",
                    onCtaClick = { onSearchClick() },
                    modifier = Modifier
                        .padding(horizontal = GagSpacing.Large)
                        .padding(bottom = GagSpacing.Large)
                )
            }

            // ─── Categories Rail ──────────────────────────────────
            item {
                CraveSectionHeader(
                    title = "Explore Categories",
                    modifier = Modifier.padding(horizontal = GagSpacing.Large)
                )
                Spacer(modifier = Modifier.height(GagSpacing.Small))
                
                when (val catState = uiState.categories) {
                    is UiState.Success -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                            horizontalArrangement = Arrangement.spacedBy(GagSpacing.Small)
                        ) {
                            item {
                                GagCategoryChip(
                                    label = "All",
                                    isSelected = true,
                                    onClick = { /* Already on All */ },
                                    emoji = "🍟"
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
                            horizontalArrangement = Arrangement.spacedBy(GagSpacing.Small)
                        ) {
                            items(6) { GagLoadingSkeleton(modifier = Modifier.size(80.dp, 36.dp)) }
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
                CraveSectionHeader(
                    title = "Popular Right Now",
                    modifier = Modifier.padding(horizontal = GagSpacing.Large)
                )
                Spacer(modifier = Modifier.height(GagSpacing.Small))
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
            item { Spacer(modifier = Modifier.height(GagSpacing.ExtraLarge)) }

            // ─── Featured Outlets ──────────────────────────────────
            item {
                CraveSectionHeader(
                    title = "Featured Campus Outlets",
                    onSeeAll = { onNavigateBottom("outlets") },
                    modifier = Modifier.padding(horizontal = GagSpacing.Large)
                )
                Spacer(modifier = Modifier.height(GagSpacing.Small))
                
                when (val outletState = uiState.outlets) {
                    is UiState.Success -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                            horizontalArrangement = Arrangement.spacedBy(GagSpacing.Medium)
                        ) {
                            items(outletState.data.take(4)) { outlet ->
                                CraveRestaurantCard(
                                    outlet = outlet,
                                    onClick = { onOutletClick(outlet.id) },
                                    modifier = Modifier.width(220.dp)
                                )
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
                CraveSectionHeader(
                    title = "Recommended for You",
                    modifier = Modifier.padding(horizontal = GagSpacing.Large)
                )
                Spacer(modifier = Modifier.height(GagSpacing.Small))
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
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = CraveRed, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "SRM KTR Campus",
                    style = MaterialTheme.typography.labelLarge,
                    color = CraveRed,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$greeting, ${userName.split(" ").firstOrNull() ?: ""}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
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
private fun ActiveOrderCard(order: Order, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val statusColor = when (order.status) {
        OrderStatus.PREPARING -> CraveInfo
        OrderStatus.READY -> CraveSuccess
        OrderStatus.ACCEPTED -> CraveInfo
        else -> CraveRed
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
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                onClick = onClick,
                containerColor = CraveRedContainer,
                contentColor = CraveRed
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
