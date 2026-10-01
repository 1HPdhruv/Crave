package com.srmfood.gag.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.cos
import kotlin.math.sin

enum class FlavorMood(val label: String, val emoji: String) {
    COMFORT("Comfort", "🧀"),
    BRIGHT("Bright", "🍋"),
    HEAT("Heat", "🌶️"),
    PLANT("Plant", "🥗")
}

@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onOutletClick: (String) -> Unit,
    onFoodClick: (String) -> Unit,
    onCartClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onOrdersClick: () -> Unit,
    onSeeAllOutlets: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedMood by remember { mutableStateOf<FlavorMood?>(null) }

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
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = GagSpacing.ExtraLarge)
        ) {
            // 1. ORBIT HEADER
            item {
                OrbitHeader(
                    userName = uiState.user?.name ?: "Student",
                    cartCount = uiState.cartItemCount,
                    onCartClick = onCartClick,
                    onNotificationsClick = onNotificationsClick
                )
            }

            // 2. EDITORIAL HEADLINE
            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = GagSpacing.Large)
                        .padding(bottom = 20.dp)
                ) {
                    Text(
                        text = "What's your\nflavor today?",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 36.sp, lineHeight = 42.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.5).sp
                    )
                }
            }

            // 3. SEARCH BAR
            item {
                OrbitSearchBar(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .padding(horizontal = GagSpacing.Large)
                        .padding(bottom = GagSpacing.Medium)
                )
            }

            // 4. MOOD PILLS
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = GagSpacing.ExtraLarge)
                ) {
                    items(FlavorMood.values()) { mood ->
                        val isSelected = selectedMood == mood
                        OrbitMoodPill(
                            mood = mood,
                            isSelected = isSelected,
                            onClick = { selectedMood = if (isSelected) null else mood }
                        )
                    }
                }
            }

            // 5. DELIVERY/PICKUP CONTEXT (minimal)
            item {
                var showAddressDialog by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .padding(horizontal = GagSpacing.Large)
                        .padding(bottom = GagSpacing.Large)
                ) {
                    DeliveryModeSelector(
                        selectedMode = uiState.orderingMode,
                        onModeSelected = { viewModel.setOrderingMode(it) }
                    )
                    Spacer(modifier = Modifier.height(GagSpacing.Small))
                    HostelAddressBanner(
                        address = uiState.hostelAddress,
                        isDelivery = uiState.orderingMode == OrderingMode.DELIVERY,
                        onChangeAddress = { showAddressDialog = true }
                    )
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

            // 6. ACTIVE ORDER
            uiState.activeOrder?.let { order ->
                item {
                    ActiveOrderCard(
                        order = order,
                        onClick = onOrdersClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = GagSpacing.Large)
                            .padding(bottom = GagSpacing.Large)
                    )
                }
            }

            // 7. TASTE RADAR
            item {
                OrbitSectionLabel(
                    title = "Taste Radar",
                    subtitle = null,
                    modifier = Modifier.padding(horizontal = GagSpacing.Large, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                when (val outletsState = uiState.outlets) {
                    is UiState.Success -> {
                        OrbitTasteRadarCard(
                            outlets = outletsState.data,
                            onOutletClick = onOutletClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .padding(horizontal = GagSpacing.Large)
                        )
                    }
                    is UiState.Loading -> GagLoadingSkeleton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(horizontal = GagSpacing.Large)
                            .clip(RoundedCornerShape(20.dp))
                    )
                    else -> {}
                }
                Spacer(modifier = Modifier.height(GagSpacing.ExtraLarge))
            }

            // 8. CAMPUS PICKS — THE BLOCK IS BUZZING
            item {
                OrbitSectionLabel(
                    title = "Campus Picks",
                    subtitle = "Popular right now",
                    modifier = Modifier.padding(horizontal = GagSpacing.Large, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            val popularFoodState = uiState.popularFood
            when (popularFoodState) {
                is UiState.Success -> {
                    item {
                        val allPopular = popularFoodState.data
                        val filteredPicks = if (selectedMood == null) allPopular else {
                            allPopular.filter { food ->
                                val name = food.name.lowercase()
                                val desc = food.description.lowercase()
                                when (selectedMood!!) {
                                    FlavorMood.COMFORT -> name.contains("biryani") || name.contains("dosa") || name.contains("pizza") || name.contains("cheese") || name.contains("burger") || desc.contains("comfort")
                                    FlavorMood.BRIGHT -> name.contains("juice") || name.contains("fruit") || name.contains("mint") || name.contains("lemon") || desc.contains("fresh")
                                    FlavorMood.HEAT -> name.contains("spicy") || name.contains("schezwan") || name.contains("pepper") || name.contains("chilli")
                                    FlavorMood.PLANT -> food.isVeg
                                }
                            }
                        }

                        if (filteredPicks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = GagSpacing.Large)
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Nothing matching this vibe right now.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // IMAGE-FIRST large horizontal cards matching reference
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredPicks) { food ->
                                    OrbitFoodCard(
                                        food = food,
                                        label = "THE BLOCK IS BUZZING",
                                        onClick = { onFoodClick(food.id) },
                                        onAddToCart = {
                                            if (food.customizations.isNotEmpty()) onFoodClick(food.id)
                                            else viewModel.addToCart(food)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                is UiState.Loading -> {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(3) {
                                GagLoadingSkeleton(
                                    modifier = Modifier
                                        .size(200.dp, 280.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
            item { Spacer(modifier = Modifier.height(GagSpacing.ExtraLarge)) }

            // 9. YOUR NEIGHBORHOOD — outlet rail
            item {
                OrbitSectionLabel(
                    title = "Your Neighborhood",
                    subtitle = "Stalls worth the walk",
                    modifier = Modifier.padding(horizontal = GagSpacing.Large, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            when (val outletState = uiState.outlets) {
                is UiState.Success -> {
                    item {
                        val sortedOutlets = outletState.data.sortedWith(
                            compareBy<Outlet> { !it.isOpen }.thenBy { it.estimatedWaitMinutes }
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(sortedOutlets) { outlet ->
                                OrbitOutletCard(
                                    outlet = outlet,
                                    onClick = { onOutletClick(outlet.id) }
                                )
                            }
                        }
                    }
                }
                is UiState.Loading -> {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(3) {
                                GagLoadingSkeleton(
                                    modifier = Modifier
                                        .size(240.dp, 200.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
            item { Spacer(modifier = Modifier.height(GagSpacing.ExtraLarge)) }

            // 10. EXPLORE BY CATEGORY
            item {
                OrbitSectionLabel(
                    title = "Explore By Category",
                    subtitle = null,
                    modifier = Modifier.padding(horizontal = GagSpacing.Large, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            when (val catState = uiState.categories) {
                is UiState.Success -> {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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
                }
                is UiState.Loading -> {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = GagSpacing.Large),
                            horizontalArrangement = Arrangement.spacedBy(GagSpacing.Medium)
                        ) {
                            items(5) { GagLoadingSkeleton(modifier = Modifier.size(90.dp, 44.dp).clip(CircleShape)) }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

// ─── Sub-Components ───────────────────────────────────────────────────────────

@Composable
private fun OrbitHeader(
    userName: String,
    cartCount: Int,
    onCartClick: () -> Unit,
    onNotificationsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = GagSpacing.Large, vertical = GagSpacing.Large),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            // Brand mark
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CRAVE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(14.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ORBIT",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 2.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = OrbitLime,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "SRM KTR · INDIA",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Notification icon — minimal
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onNotificationsClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
            // Cart icon with badge
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onCartClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ShoppingBag,
                    contentDescription = "Cart",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                if (cartCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(OrbitLime),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (cartCount > 9) "9+" else cartCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            fontWeight = FontWeight.Black,
                            color = OrbitOnLime
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrbitSearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Dish, mood, or place",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OrbitMoodPill(mood: FlavorMood, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) OrbitLime else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "mood_bg_${mood.label}"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) OrbitOnLime else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "mood_text_${mood.label}"
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = if (isSelected) 0.dp else 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = mood.emoji, fontSize = 14.sp)
        Text(
            text = mood.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
private fun OrbitSectionLabel(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// IMAGE-FIRST food card matching reference — large vertical card with overlay label
@Composable
private fun OrbitFoodCard(
    food: FoodItem,
    label: String,
    onClick: () -> Unit,
    onAddToCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(200.dp)
            .height(280.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
    ) {
        // Large food image fills entire card
        AsyncImage(
            model = food.imageUrl,
            contentDescription = food.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay from bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.1f),
                            Color.Black.copy(alpha = 0.75f)
                        ),
                        startY = 80f
                    )
                )
        )

        // "THE BLOCK IS BUZZING" label — top left
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(OrbitLime)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "⚡ $label",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Bold,
                color = OrbitOnLime,
                maxLines = 1
            )
        }

        // Heart / favorite button — top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorite",
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }

        // Food name + recommendation pill at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = food.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rating / time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = GagStarYellow, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "4.8",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Text(
                        text = " · ${food.price.toInt()}₹",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                // "Fast near you" lime pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(OrbitLime)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Fast near you",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold,
                        color = OrbitOnLime
                    )
                }
            }
        }
    }
}

// IMAGE-FIRST outlet card matching reference
@Composable
private fun OrbitOutletCard(outlet: Outlet, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(240.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = outlet.imageUrl,
            contentDescription = outlet.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.65f)
                        ),
                        startY = 80f
                    )
                )
        )

        // Closed overlay
        if (!outlet.isOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Closed", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // Heart
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorite",
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }

        // Outlet info at bottom
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(
                text = outlet.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, null, tint = GagStarYellow, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "${outlet.rating}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Text(
                    text = " | ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Icon(Icons.Outlined.Schedule, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "${outlet.estimatedWaitMinutes}–${outlet.estimatedWaitMinutes + 6} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// TASTE RADAR — dark map-inspired card with discovery points
@Composable
private fun OrbitTasteRadarCard(
    outlets: List<Outlet>,
    onOutletClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val openOutlets = outlets.filter { it.isOpen }.take(4)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0A0A0A))
    ) {
        // Background grid lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val gridColor = Color(0xFF1E1E1E)

            // Horizontal grid lines
            val hStep = h / 6
            for (i in 1..5) {
                drawLine(gridColor, Offset(0f, i * hStep), Offset(w, i * hStep), strokeWidth = 1f)
            }
            // Vertical grid lines
            val vStep = w / 8
            for (i in 1..7) {
                drawLine(gridColor, Offset(i * vStep, 0f), Offset(i * vStep, h), strokeWidth = 1f)
            }
            // Campus block shapes
            val blockColor = Color(0xFF141414)
            drawRoundRect(
                color = blockColor,
                topLeft = Offset(w * 0.1f, h * 0.2f),
                size = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.3f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f)
            )
            drawRoundRect(
                color = blockColor,
                topLeft = Offset(w * 0.45f, h * 0.1f),
                size = androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.25f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f)
            )
            drawRoundRect(
                color = blockColor,
                topLeft = Offset(w * 0.7f, h * 0.5f),
                size = androidx.compose.ui.geometry.Size(w * 0.16f, h * 0.35f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f)
            )
            drawRoundRect(
                color = blockColor,
                topLeft = Offset(w * 0.3f, h * 0.6f),
                size = androidx.compose.ui.geometry.Size(w * 0.22f, h * 0.25f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f)
            )

            // Glowing discovery points
            val discoveryColors = listOf(
                Color(0xFFCDEA45), // lime
                Color(0xFF60A5FA), // blue
                Color(0xFFFF8C00), // orange
                Color(0xFFA78BFA), // purple
            )
            val positions = listOf(
                Offset(w * 0.22f, h * 0.58f),
                Offset(w * 0.55f, h * 0.35f),
                Offset(w * 0.75f, h * 0.25f),
                Offset(w * 0.38f, h * 0.78f),
            )
            positions.take(openOutlets.size + 1).forEachIndexed { i, pos ->
                val color = discoveryColors[i % discoveryColors.size]
                // Glow
                drawCircle(color = color.copy(alpha = 0.15f), radius = 24f, center = pos)
                drawCircle(color = color.copy(alpha = 0.3f), radius = 14f, center = pos)
                drawCircle(color = color, radius = 7f, center = pos)
            }
        }

        // TASTE RADAR label top left
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
        ) {
            Text(
                text = "TASTE RADAR",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = OrbitLime,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "${openOutlets.size + (if (openOutlets.size < 4) 1 else 0)} spots nearby",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        // Outlet marker bubbles — clickable
        val markerPositions = listOf(
            Pair(0.55f, 0.28f),  // center-ish
            Pair(0.72f, 0.18f),  // top right
            Pair(0.35f, 0.65f),  // bottom center
            Pair(0.18f, 0.48f),  // left
        )
        openOutlets.take(4).forEachIndexed { i, outlet ->
            val (xFrac, yFrac) = markerPositions[i % markerPositions.size]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = (xFrac * 240).dp,
                        top = (yFrac * 180).dp
                    )
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(OrbitDarkSurface.copy(alpha = 0.9f))
                        .border(1.dp, OrbitLime.copy(alpha = 0.5f), CircleShape)
                        .clickable { onOutletClick(outlet.id) }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = outlet.name.take(12),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveOrderCard(order: Order, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val statusColor = when (order.status) {
        OrderStatus.PREPARING -> OrbitBlue
        OrderStatus.READY -> OrbitSuccess
        OrderStatus.ACCEPTED -> OrbitBlue
        else -> OrbitLime
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ACTIVE ORDER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = order.outletName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(statusColor))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = order.status.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(OrbitLime)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Track →",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = OrbitOnLime
                )
            }
        }
    }
}

@Composable
private fun GagCategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    iconUrl: String? = null,
    emoji: String? = null
) {
    val backgroundColor = if (isSelected) OrbitLime else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) OrbitOnLime else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 0.dp else 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = CircleShape
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (emoji != null) {
                Text(text = emoji, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}
