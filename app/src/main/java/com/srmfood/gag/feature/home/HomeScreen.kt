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
import com.srmfood.gag.core.ui.component.CartIconButton
import com.srmfood.gag.core.ui.component.FoodItemCard
import com.srmfood.gag.core.ui.component.GagBottomNavBar
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.ShimmerBox
import com.srmfood.gag.core.ui.component.studentBottomNavItems
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
        containerColor = GagBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // ─── Header ──────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(GagOrangeDark.copy(alpha = 0.1f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 20.dp)
                        .statusBarsPadding()
                        .padding(top = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.bodyMedium,
                                color = GagOnSurfaceVariant
                            )
                            Text(
                                text = uiState.user?.name ?: "Loading...",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Campus badge
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = GagSurface
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocationOn, null, tint = GagOrange, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SRM KTR", style = MaterialTheme.typography.labelSmall, color = GagOnSurface)
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { onNavigateBottom("notifications") }) {
                                Icon(Icons.Default.Notifications, "Notifications", tint = GagOnBackground)
                            }
                            CartIconButton(
                                cartCount = uiState.cartItemCount,
                                onClick = onCartClick
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Search Bar
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = GagSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSearchClick)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, null, tint = GagOnSurfaceVariant, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Search food, outlets…", style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // ─── Active Order Card ────────────────────────────────
            uiState.activeOrder?.let { order ->
                item {
                    ActiveOrderCard(
                        order = order,
                        onClick = { onNavigateBottom("orders") },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // ─── Categories ───────────────────────────────────────
            item {
                SectionHeader(title = "Categories", onSeeAll = null)
                when (val catState = uiState.categories) {
                    is UiState.Success -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(catState.data) { category ->
                                CategoryChip(category = category, onClick = { onSearchClick() })
                            }
                        }
                    }
                    is UiState.Loading -> {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(6) { ShimmerBox(modifier = Modifier.size(80.dp, 88.dp), cornerRadius = 16.dp) }
                        }
                    }
                    is UiState.Error -> {
                        Text(
                            text = catState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    else -> {}
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ─── Nearby Outlets ───────────────────────────────────
            item {
                SectionHeader(title = "Nearby Outlets", onSeeAll = { onNavigateBottom("outlets") })
                when (val outletState = uiState.outlets) {
                    is UiState.Success -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(outletState.data.take(4)) { outlet ->
                                OutletCard(outlet = outlet, onClick = { onOutletClick(outlet.id) })
                            }
                        }
                    }
                    is UiState.Loading -> {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(3) { ShimmerBox(modifier = Modifier.size(200.dp, 160.dp), cornerRadius = 16.dp) }
                        }
                    }
                    is UiState.Error -> {
                        Text(
                            text = outletState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    else -> {}
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ─── Popular Foods ────────────────────────────────────
            item {
                SectionHeader(title = "🔥 Popular Right Now", onSeeAll = null)
            }
            when (val popularState = uiState.popularFood) {
                is UiState.Success -> {
                    items(popularState.data.take(5)) { food ->
                        FoodItemCard(
                            foodItem = food,
                            onClick = { onFoodClick(food.id) },
                            onAddToCart = { viewModel.addToCart(food) },
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 10.dp)
                        )
                    }
                }
                is UiState.Loading -> {
                    items(3) {
                        ShimmerBox(modifier = Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 20.dp, vertical = 5.dp))
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(
                            text = popularState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp)
                        )
                    }
                }
                else -> {}
            }

            // ─── Recommended ─────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(title = "⭐ Recommended for You", onSeeAll = null)
            }
            when (val recState = uiState.recommendedFood) {
                is UiState.Success -> {
                    items(recState.data.take(4)) { food ->
                        FoodItemCard(
                            foodItem = food,
                            onClick = { onFoodClick(food.id) },
                            onAddToCart = { viewModel.addToCart(food) },
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 10.dp)
                        )
                    }
                }
                is UiState.Loading -> {
                    items(3) {
                        ShimmerBox(modifier = Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 20.dp, vertical = 5.dp))
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(
                            text = recState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp)
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
private fun SectionHeader(title: String, onSeeAll: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        onSeeAll?.let {
            Row(modifier = Modifier.clickable(onClick = it), verticalAlignment = Alignment.CenterVertically) {
                Text("See all", style = MaterialTheme.typography.labelMedium, color = GagOrange)
                Icon(Icons.Default.ArrowForward, null, tint = GagOrange, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun CategoryChip(category: FoodCategory, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GagSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = category.emoji, fontSize = 28.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = category.name, style = MaterialTheme.typography.labelSmall, color = GagOnSurface)
    }
}

@Composable
private fun OutletCard(outlet: Outlet, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GagSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(GagSurfaceVariant)
            ) {
                AsyncImage(
                    model = outlet.imageUrl,
                    contentDescription = outlet.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                // Open/closed badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (outlet.isOpen) GagSuccessContainer else GagErrorContainer,
                    modifier = Modifier.padding(8.dp).align(Alignment.TopStart)
                ) {
                    Text(
                        text = if (outlet.isOpen) "Open" else "Closed",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (outlet.isOpen) GagSuccess else GagError,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = outlet.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = outlet.location.building, style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                if (outlet.isOpen && outlet.estimatedWaitMinutes > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null, tint = GagAmber, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("~${outlet.estimatedWaitMinutes} min wait", style = MaterialTheme.typography.labelSmall, color = GagOnSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveOrderCard(order: Order, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val statusColor = when (order.status) {
        OrderStatus.PREPARING -> StatusPreparing
        OrderStatus.READY -> StatusReady
        OrderStatus.ACCEPTED -> StatusAccepted
        else -> GagInfo
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = GagSurface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Circle, null, tint = statusColor, modifier = Modifier.size(8.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Active Order · ${order.status.displayName}", style = MaterialTheme.typography.labelMedium, color = statusColor, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(order.orderNumber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(order.outletName, style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
            }
            Icon(Icons.Default.ArrowForward, "View order", tint = GagOrange)
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
    return "$greet, $firstName 👋"
}
