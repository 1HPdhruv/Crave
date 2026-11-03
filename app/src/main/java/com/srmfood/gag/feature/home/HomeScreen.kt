@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.srmfood.gag.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    /** "See all" style entry points browse the catalogue (Explore); the search bar opens the search page. */
    onBrowseClick: () -> Unit = onSearchClick,
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
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. TOP BAR — wordmark · bell · bag pill
            item {
                CraveHomeTopBar(
                    cartCount = uiState.cartItemCount,
                    onCartClick = onCartClick,
                    onNotificationsClick = onNotificationsClick
                )
            }

            // 2. INTRO — date eyebrow, greeting, lede
            item {
                val firstName = uiState.user?.name?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: "there"
                Column(modifier = Modifier.padding(horizontal = 17.dp).padding(top = 10.dp, bottom = 18.dp)) {
                    CraveEyebrow(todayLabel(), color = CraveOrangeDeep)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = buildAnnotatedString {
                            append(greetingFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))
                            append("\n")
                            withStyle(SpanStyle(color = CraveOrange)) { append("Make it count, $firstName.") }
                        },
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 42.sp, lineHeight = 40.sp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Pick a pickup slot, grab your token, skip the queue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    CraveSearchBar(
                        query = "",
                        onQueryChange = {},
                        readOnly = true,
                        placeholder = "Search a craving…",
                        onClick = onSearchClick
                    )
                }
            }

            // 3. DELIVERY / PICKUP CONTEXT
            item {
                var showAddressDialog by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .padding(horizontal = 17.dp)
                        .padding(bottom = 16.dp)
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

            // 4. ACTIVE ORDER
            uiState.activeOrder?.let { order ->
                item {
                    ActiveOrderCard(
                        order = order,
                        onClick = onOrdersClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 17.dp)
                            .padding(bottom = 8.dp)
                    )
                }
            }

            // 5. START HERE — categories
            item {
                CraveHeading(
                    eyebrow = "Start here",
                    title = "What’s the vibe?",
                    modifier = Modifier.padding(horizontal = 17.dp).padding(top = 28.dp),
                    actionText = "see all eats",
                    onAction = onBrowseClick
                )
                Spacer(Modifier.height(16.dp))
            }
            when (val catState = uiState.categories) {
                is UiState.Success -> item {
                    CategoryChips(categories = catState.data, onCategoryClick = onCategoryClick)
                }
                is UiState.Loading -> item {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 17.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(6) { GagLoadingSkeleton(Modifier.size(92.dp, 38.dp).clip(CircleShape)) }
                    }
                }
                is UiState.Error -> item {
                    HomeSectionMessage("Couldn’t load categories", catState.message, onRetry = viewModel::loadData)
                }
                else -> item { HomeSectionMessage("No categories yet", "Menus are being set up.") }
            }

            // 6. AROUND CAMPUS — outlets, 2 per row
            item {
                CraveHeading(
                    eyebrow = "Around campus",
                    title = "Outlets ✦",
                    modifier = Modifier.padding(horizontal = 17.dp).padding(top = 40.dp),
                    actionText = "all outlets",
                    onAction = onSeeAllOutlets
                )
                Spacer(Modifier.height(16.dp))
            }
            when (val outletState = uiState.outlets) {
                is UiState.Success -> {
                    val sorted = outletState.data.sortedWith(
                        compareBy<Outlet> { !it.isOpen }.thenBy { it.estimatedWaitMinutes }
                    )
                    craveGridItems(sorted, key = { it.id }) { outlet ->
                        CraveRestaurantCard(
                            outlet = outlet,
                            onClick = { onOutletClick(outlet.id) },
                            fillWidth = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is UiState.Loading -> craveGridItems(List(4) { it }) {
                    GagLoadingSkeleton(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(15.dp)))
                }
                is UiState.Error -> item {
                    HomeSectionMessage("Couldn’t load outlets", outletState.message, onRetry = viewModel::loadData)
                }
                else -> item { HomeSectionMessage("No outlets are open yet", "Check back soon — campus kitchens are warming up.") }
            }

            // 7. CAMPUS FAVOURITES — popular food, 2 per row
            item {
                CraveHeading(
                    eyebrow = "Campus favourites",
                    title = "Popular right now",
                    modifier = Modifier.padding(horizontal = 17.dp).padding(top = 32.dp),
                    actionText = "more",
                    onAction = onBrowseClick
                )
                Spacer(Modifier.height(16.dp))
            }
            when (val popular = uiState.popularFood) {
                is UiState.Success -> craveGridItems(popular.data, key = { it.id }) { food ->
                    FoodItemCard(
                        foodItem = food,
                        onClick = { onFoodClick(food.id) },
                        onAddToCart = {
                            if (food.customizations.isNotEmpty()) onFoodClick(food.id)
                            else viewModel.addToCart(food)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is UiState.Loading -> craveGridItems(List(4) { it }) {
                    GagLoadingSkeleton(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(16.dp)))
                }
                is UiState.Error -> item {
                    HomeSectionMessage("Couldn’t load popular items", popular.message, onRetry = viewModel::loadData)
                }
                else -> item { HomeSectionMessage("Nothing popular yet", "Order something and be the trend.") }
            }
        }
    }
}

// ─── Sub-Components ───────────────────────────────────────────────────────────

private fun greetingFor(hour: Int): String = when {
    hour < 5 -> "Late-night craving?"
    hour < 11 -> "Breakfast run?"
    hour < 16 -> "Lunch break?"
    hour < 19 -> "Snack o’clock?"
    else -> "Late-night craving?"
}

private fun todayLabel(): String =
    java.text.SimpleDateFormat("EEEE, dd MMMM", java.util.Locale("en", "IN")).format(java.util.Date()).uppercase()

/** Website category emoji (the backend carries no emoji for derived categories). */
private fun categoryEmoji(name: String): String {
    val n = name.lowercase()
    return when {
        "biryani" in n || "rice" in n || "meal" in n -> "🍛"
        "pizza" in n -> "🍕"
        "burger" in n || "fast" in n -> "🍔"
        "dosa" in n || "south" in n || "tiffin" in n -> "🥞"
        "chinese" in n || "noodle" in n -> "🥡"
        "beverage" in n || "drink" in n || "juice" in n || "shake" in n -> "🥤"
        "coffee" in n || "tea" in n -> "☕"
        "dessert" in n || "sweet" in n || "ice" in n -> "🍨"
        "snack" in n || "chat" in n || "chaat" in n -> "🍿"
        "egg" in n || "omelet" in n -> "🍳"
        "sandwich" in n || "bread" in n -> "🥪"
        "roll" in n || "wrap" in n -> "🌯"
        else -> "🍽️"
    }
}

@Composable
private fun CraveHomeTopBar(
    cartCount: Int,
    onCartClick: () -> Unit,
    onNotificationsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 17.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            CraveBrandMark(compact = true)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(CraveChartreuse)
                    .border(1.dp, CraveInk, CircleShape)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(CraveOrange))
                Spacer(Modifier.width(6.dp))
                Text("SRMIST", style = GagEyebrowStyle.copy(fontSize = 9.sp), color = CraveInk)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .clickable(onClick = onNotificationsClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(19.dp)
                )
            }
            // Bag pill: ink fill, orange count, chartreuse arrow
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CraveInk)
                    .clickable(onClick = onCartClick)
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bag", style = MaterialTheme.typography.labelMedium, color = CravePaper)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(20.dp).clip(CircleShape).background(CraveOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (cartCount > 9) "9+" else cartCount.toString(),
                        style = GagEyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.sp),
                        color = CraveInk
                    )
                }
                Text(" ↗", color = CraveChartreuse, fontSize = 15.sp)
            }
        }
    }
}

/** Number of categories shown before "Show more". */
internal const val COLLAPSED_CATEGORY_COUNT = 5

/** Categories to render: all of them when expanded or when there are five or fewer, otherwise the first five. */
internal fun <T> visibleCategories(all: List<T>, expanded: Boolean): List<T> =
    if (all.size > COLLAPSED_CATEGORY_COUNT && !expanded) all.take(COLLAPSED_CATEGORY_COUNT) else all

/** Category pills: first five by default, the rest behind a subtle Show more / Show less toggle. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(categories: List<FoodCategory>, onCategoryClick: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val collapsible = categories.size > COLLAPSED_CATEGORY_COUNT
    val visible = visibleCategories(categories, expanded)

    FlowRow(
        modifier = Modifier.padding(horizontal = 17.dp).animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        visible.forEach { category ->
            CraveCategoryPill(
                label = category.name,
                emoji = categoryEmoji(category.name),
                onClick = { onCategoryClick(category.name) }
            )
        }
        if (collapsible) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClickLabel = if (expanded) "Show fewer categories" else "Show more categories") { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Show less" else "Show more",
                    style = MaterialTheme.typography.labelMedium,
                    color = CraveOrangeDeep,
                    maxLines = 1
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = CraveOrangeDeep,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CraveCategoryPill(label: String, emoji: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** Empty / error block for a home section (replaces the old silent blank). */
@Composable
private fun HomeSectionMessage(title: String, message: String, onRetry: (() -> Unit)? = null) {
    CravePanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onRetry != null) {
                Spacer(Modifier.height(12.dp))
                GagSecondaryButton(text = "Try again", onClick = onRetry, modifier = Modifier.width(140.dp))
            }
        }
    }
}

/** Website `.active-order-card`: orange brutal card with a Track action. */
@Composable
private fun ActiveOrderCard(order: Order, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CraveBrutalCard(modifier = modifier.clickable(onClick = onClick), container = CraveOrange) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CraveSticker("Active order", container = CraveInk, content = CravePaper)
                Text(order.orderNumber, style = GagMonoStyle.copy(fontSize = 11.sp), color = Color(0xFF6B2D20))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = order.outletName,
                style = MaterialTheme.typography.headlineMedium,
                color = CraveInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = order.status.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF65291D)
            )
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CraveInk)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text("Track order ↗", style = MaterialTheme.typography.labelMedium, color = CravePaper, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
