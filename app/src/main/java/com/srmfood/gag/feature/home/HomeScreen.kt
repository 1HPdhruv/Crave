package com.srmfood.gag.feature.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.*
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.OrderStatus
import com.srmfood.gag.domain.model.Outlet
import com.srmfood.gag.domain.repository.HostelAddress
import com.srmfood.gag.domain.repository.OrderingMode
import java.util.Calendar

private val OrbitAccent = Color(0xFFC9EF52)
private val OrbitTeal = Color(0xFF66B9A8)
private val OrbitCoral = Color(0xFFFF805B)

private data class FlavorMood(val label: String, val emoji: String)
private val flavorMoods = listOf(
    FlavorMood("Comfort", "♨"),
    FlavorMood("Bright", "☼"),
    FlavorMood("Heat", "♨"),
    FlavorMood("Plant", "⌁")
)

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
    var selectedMood by rememberSaveable { mutableStateOf("All") }
    var showAddressDialog by rememberSaveable { mutableStateOf(false) }
    val firstName = uiState.user?.name?.trim()?.split(" ")?.firstOrNull().orEmpty().ifBlank { "there" }

    val allFoods = buildList {
        (uiState.popularFood as? UiState.Success)?.data?.let(::addAll)
        (uiState.recommendedFood as? UiState.Success)?.data?.let(::addAll)
    }.distinctBy { it.id }
    val moodFoods = remember(allFoods, selectedMood) { filterForMood(allFoods, selectedMood) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
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
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                OrbitTopBar(
                    cartCount = uiState.cartItemCount,
                    onCartClick = onCartClick,
                    onNotificationsClick = { onNavigateBottom("notifications") },
                    onProfileClick = { onNavigateBottom("profile") }
                )
            }

            item {
                Column(Modifier.padding(horizontal = 22.dp)) {
                    Text(
                        text = "SRM KTR  ·  CAMPUS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "What’s your\nflavor today?",
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 39.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                    text = "Good ${getGreeting()}, $firstName. Find your next campus favorite.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(18.dp))
                    GagSearchBar(
                        query = "",
                        onQueryChange = {},
                        placeholder = "Dish, mood, or place",
                        onClick = onSearchClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp).padding(top = 18.dp, bottom = 8.dp)
                ) {
                    DeliveryModeSelector(
                        selectedMode = uiState.orderingMode,
                        onModeSelected = viewModel::setOrderingMode
                    )
                    Spacer(Modifier.height(8.dp))
                    HostelAddressBanner(
                        address = uiState.hostelAddress,
                        isDelivery = uiState.orderingMode == OrderingMode.DELIVERY,
                        onChangeAddress = { showAddressDialog = true }
                    )
                    if (uiState.orderingMode == OrderingMode.PICKUP) {
                        PickupRestaurantBanner(outletName = "Choose a stall below for pickup")
                    }
                }
            }

            uiState.activeOrder?.let { order ->
                item {
                    ActiveOrderCard(
                        order = order,
                        onClick = { onNavigateBottom("orders") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 10.dp)
                    )
                }
            }

            item {
                Column(Modifier.padding(top = 18.dp, bottom = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("PICK A FEELING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(3.dp))
                            Text("Follow the craving", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        }
                        TextButton(onClick = { selectedMood = "All" }) { Text("Reset") }
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        item { FlavorMoodChip("All", "✳", selectedMood == "All") { selectedMood = "All" } }
                        items(flavorMoods) { mood ->
                            FlavorMoodChip(mood.label, mood.emoji, selectedMood == mood.label) { selectedMood = mood.label }
                        }
                    }
                }
            }

            item {
                OrbitRadarCard(
                    openOutletCount = (uiState.outlets as? UiState.Success)?.data?.count { it.isOpen } ?: 0,
                    totalOutletCount = (uiState.outlets as? UiState.Success)?.data?.size ?: 0,
                    onClick = { onNavigateBottom("outlets") },
                    modifier = Modifier.padding(horizontal = 22.dp).padding(top = 8.dp, bottom = 24.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("CAMPUS PICKS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (selectedMood == "All") "Popular right now" else "$selectedMood, served fresh",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    TextButton(onClick = onSearchClick) { Text("Explore all") }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (moodFoods.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(moodFoods.take(8), key = { "food-${it.id}" }) { food ->
                            FoodItemCard(
                                foodItem = food,
                                onClick = { onFoodClick(food.id) },
                                onAddToCart = {
                                    if (food.customizations.isNotEmpty()) onFoodClick(food.id) else viewModel.addToCart(food)
                                },
                                modifier = Modifier.width(224.dp)
                            )
                        }
                    }
                }
            } else if (uiState.popularFood is UiState.Loading || uiState.recommendedFood is UiState.Loading) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) { items(3) { GagLoadingSkeleton(Modifier.size(224.dp, 245.dp)) } }
                }
            } else if (selectedMood != "All") {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                        GagEmptyState(
                            title = "No $selectedMood matches just yet",
                            description = "Try another craving or search all campus menus.",
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp)).padding(12.dp),
                            actionButton = { GagPrimaryButton("Browse all food", onSearchClick) }
                        )
                    }
                }
            } else {
                item {
                    GagErrorState(
                        message = when {
                            uiState.popularFood is UiState.Error -> (uiState.popularFood as UiState.Error).message
                            uiState.recommendedFood is UiState.Error -> (uiState.recommendedFood as UiState.Error).message
                            else -> "No food is available right now. Please try again."
                        },
                        onRetry = viewModel::loadData,
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 24.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("YOUR NEIGHBORHOOD", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(3.dp))
                        Text("Stalls worth the walk", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                    TextButton(onClick = { onNavigateBottom("outlets") }) { Text("See all") }
                }
            }

            when (val outletState = uiState.outlets) {
                is UiState.Success -> {
                    if (outletState.data.isEmpty()) {
                        item { GagEmptyState("No stalls nearby", "Check back soon for campus favorites.", Modifier.padding(horizontal = 22.dp)) }
                    } else {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 22.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(outletState.data.sortedWith(compareByDescending<Outlet> { it.isOpen }.thenBy { it.estimatedWaitMinutes }).take(8), key = { "outlet-${it.id}" }) { outlet ->
                                    CraveRestaurantCard(
                                        outlet = outlet,
                                        onClick = { onOutletClick(outlet.id) },
                                        modifier = Modifier.width(232.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                is UiState.Loading -> item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(3) { GagLoadingSkeleton(Modifier.size(232.dp, 180.dp)) }
                    }
                }
                is UiState.Error -> item {
                    GagErrorState(outletState.message, viewModel::loadData, Modifier.padding(horizontal = 22.dp))
                }
                else -> Unit
            }

            item {
                when (val catState = uiState.categories) {
                    is UiState.Success -> {
                        Column(Modifier.padding(top = 24.dp, bottom = 14.dp)) {
                            CraveSectionHeader("Explore by category", Modifier.padding(horizontal = 22.dp))
                            Spacer(Modifier.height(12.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                items(catState.data, key = { "category-${it.id}" }) { category ->
                                    GagCategoryChip(
                                        label = "${category.emoji} ${category.name}",
                                        isSelected = false,
                                        onClick = { onCategoryClick(category.name) }
                                    )
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
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

@Composable
private fun OrbitTopBar(
    cartCount: Int,
    onCartClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.clickable(onClick = onProfileClick)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CRAVE", style = MaterialTheme.typography.titleLarge.copy(fontSize = 23.sp), fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.width(7.dp))
                Box(Modifier.width(2.dp).height(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text("ORBIT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, letterSpacing = 2.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text("SRM KTR · INDIA", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OrbitIconButton(Icons.Default.Notifications, "Notifications", onNotificationsClick)
            Box {
                OrbitIconButton(Icons.Default.ShoppingCart, "Open cart", onCartClick)
                if (cartCount > 0) {
                    Box(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).size(17.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Text(if (cartCount > 9) "9+" else cartCount.toString(), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrbitIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FlavorMoodChip(label: String, glyph: String, selected: Boolean, onClick: () -> Unit) {
    val accent = when (label) {
        "Heat" -> OrbitCoral
        "Bright" -> OrbitTeal
        "Plant" -> Color(0xFF78A85F)
        else -> OrbitAccent
    }
    Row(
        modifier = Modifier.clip(CircleShape)
            .background(if (selected) accent else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .then(if (!selected) Modifier else Modifier)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(glyph, color = if (selected) Color(0xFF182120) else accent, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) Color(0xFF182120) else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun OrbitRadarCard(
    openOutletCount: Int,
    totalOutletCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = MaterialTheme.colorScheme.surfaceVariant
    val mapSurface = MaterialTheme.colorScheme.surface
    val road = MaterialTheme.colorScheme.outline.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.4f) 0.32f else 0.42f)
    val mapAccent = OrbitAccent
    val mapTeal = OrbitTeal
    val mapCoral = OrbitCoral

    Box(
        modifier = modifier.fillMaxWidth().height(178.dp).clip(RoundedCornerShape(26.dp)).background(background).clickable(onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.linearGradient(listOf(background, background.copy(alpha = 0.62f), background)))
            // Abstract, clearly schematic campus blocks and streets.
            for (i in 0..7) {
                val x = w * (0.06f + i * 0.14f)
                drawLine(road, Offset(x, 0f), Offset(x - w * 0.08f, h), strokeWidth = 2.dp.toPx())
            }
            for (i in 0..5) {
                val y = h * (0.12f + i * 0.18f)
                drawLine(road, Offset(0f, y), Offset(w, y - h * 0.08f), strokeWidth = 2.dp.toPx())
            }
            val blocks = listOf(
                Triple(0.08f, 0.22f, 0.15f), Triple(0.37f, 0.16f, 0.12f),
                Triple(0.70f, 0.28f, 0.18f), Triple(0.22f, 0.62f, 0.18f),
                Triple(0.56f, 0.66f, 0.14f), Triple(0.80f, 0.67f, 0.11f)
            )
            blocks.forEachIndexed { index, block ->
                drawRoundRect(
                    color = if (index % 2 == 0) mapSurface.copy(alpha = 0.6f) else road.copy(alpha = 0.32f),
                    topLeft = Offset(w * block.first, h * block.second),
                    size = Size(w * block.third, h * 0.11f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                )
            }
            val center = Offset(w * 0.52f, h * 0.54f)
            listOf(24.dp.toPx(), 42.dp.toPx(), 61.dp.toPx()).forEach { radius ->
                drawCircle(mapAccent.copy(alpha = 0.2f), radius = radius, center = center, style = Stroke(width = 1.dp.toPx()))
            }
            val pins = listOf(Offset(w * 0.18f, h * 0.42f), Offset(w * 0.49f, h * 0.22f), Offset(w * 0.82f, h * 0.55f))
            val pinColors = listOf(mapTeal, mapAccent, mapCoral)
            pins.forEachIndexed { index, point ->
                drawLine(pinColors[index].copy(alpha = 0.55f), center, point, strokeWidth = 1.4.dp.toPx())
                drawCircle(pinColors[index].copy(alpha = 0.14f), radius = 19.dp.toPx(), center = point)
                drawCircle(pinColors[index], radius = 5.dp.toPx(), center = point)
            }
            drawCircle(mapAccent, radius = 5.dp.toPx(), center = center)
        }

        Column(Modifier.align(Alignment.TopStart).padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Text("TASTE RADAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(5.dp))
            val availability = when {
                totalOutletCount == 0 -> "Campus spots are loading"
                else -> "$openOutletCount open · $totalOutletCount campus spots"
            }
            Text(availability, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 1.dp
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Explore stalls", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(15.dp))
            }
        }
    }
}

internal fun filterForMood(items: List<FoodItem>, mood: String): List<FoodItem> {
    if (mood == "All") return items
    val terms = when (mood) {
        "Comfort" -> listOf("biryani", "burger", "pizza", "noodle", "rice", "wrap", "fries", "samosa", "pasta", "sandwich", "comfort")
        "Bright" -> listOf("salad", "fresh", "light", "citrus", "lemon", "juice", "fruit", "bowl", "green", "smoothie")
        "Heat" -> listOf("spicy", "chili", "chilli", "hot", "pepper", "masala", "fiery", "heat", "schezwan")
        "Plant" -> emptyList()
        else -> return items
    }
    return items.filter { food ->
        if (mood == "Plant") return@filter food.isVeg
        val searchable = listOf(food.name, food.category, food.description, food.tags.joinToString(" "))
            .joinToString(" ").lowercase()
        terms.any(searchable::contains)
    }
}

@Composable
private fun ActiveOrderCard(order: Order, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val statusColor = when (order.status) {
        OrderStatus.PREPARING -> GagInfo
        OrderStatus.READY -> GagSuccess
        OrderStatus.ACCEPTED -> GagInfo
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = statusColor, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Circle, null, tint = statusColor, modifier = Modifier.size(7.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ORDER IN MOTION · ${order.status.displayName.uppercase()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = statusColor)
                }
                Spacer(Modifier.height(4.dp))
                Text(order.outletName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(order.orderNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Track order", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun getGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "morning"
        hour < 17 -> "afternoon"
        else -> "evening"
    }
}

private fun Color.luminance(): Float {
    val red = this.red.coerceIn(0f, 1f)
    val green = this.green.coerceIn(0f, 1f)
    val blue = this.blue.coerceIn(0f, 1f)
    return (0.2126f * red) + (0.7152f * green) + (0.0722f * blue)
}
