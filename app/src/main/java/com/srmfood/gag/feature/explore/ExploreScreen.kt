package com.srmfood.gag.feature.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.CraveFilterChip
import com.srmfood.gag.core.ui.component.CraveSectionHeader
import com.srmfood.gag.core.ui.component.FoodItemCard
import com.srmfood.gag.core.ui.component.GagCategoryChip
import com.srmfood.gag.core.ui.component.GagEmptyScreen
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.SortOption
import com.srmfood.gag.feature.search.SearchUiEvent
import com.srmfood.gag.feature.search.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onFoodClick: (String) -> Unit,
    onOutletClick: (String) -> Unit,
    onAddToCart: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    var showVegSheet by remember { mutableStateOf(false) }
    var showPriceSheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SearchUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    if (uiState.showMixedOutletDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMixedOutletDialog,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Different Outlet", fontWeight = FontWeight.Bold) },
            text = { Text("Your cart contains items from a different outlet. Clear cart and add from this outlet?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.onClearAndAddCart() },
                    colors = ButtonDefaults.buttonColors(containerColor = GagPink)
                ) { Text("Clear & Add") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMixedOutletDialog) { Text("Keep Cart", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                // Header (EXPLORE)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "EXPLORE",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Find your next bite.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Search Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        OutlinedTextField(
                            value = uiState.query,
                            onValueChange = viewModel::onQueryChanged,
                            placeholder = { 
                                Text(
                                    text = "Dish, mood, or place", 
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ) 
                            },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            trailingIcon = {
                                if (uiState.query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                        Icon(Icons.Default.Close, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { 
                                viewModel.search()
                                keyboard?.hide() 
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                cursorColor = GagPink
                            ),
                            modifier = Modifier.focusRequester(focusRequester)
                        )
                    }
                }
                
                // Horizontal Filter Row (Always visible)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val hasFilters = uiState.filterVegOnly != null || uiState.filterMaxPrice != null || uiState.selectedCategory != null || uiState.sortBy != SortOption.RELEVANCE
                    if (hasFilters) {
                        CraveFilterChip(
                            label = "Clear",
                            isSelected = false,
                            onClick = {
                                viewModel.onVegFilterChanged(null)
                                viewModel.onMaxPriceChanged(null)
                                viewModel.onCategorySelected(null)
                                viewModel.onSortChanged(SortOption.RELEVANCE)
                                viewModel.search()
                            },
                            leadingIcon = Icons.Default.Close
                        )
                    }

                    CraveFilterChip(
                        label = when(uiState.filterVegOnly) {
                            true -> "Veg"
                            false -> "Non-Veg"
                            null -> "Food Type"
                        },
                        isSelected = uiState.filterVegOnly != null,
                        onClick = { showVegSheet = true }
                    )

                    CraveFilterChip(
                        label = if (uiState.filterMaxPrice != null) "Under ₹${uiState.filterMaxPrice!!.toInt()}" else "Price",
                        isSelected = uiState.filterMaxPrice != null,
                        onClick = { showPriceSheet = true }
                    )

                    CraveFilterChip(
                        label = uiState.selectedCategory ?: "Category",
                        isSelected = uiState.selectedCategory != null,
                        onClick = { showCategorySheet = true }
                    )

                    CraveFilterChip(
                        label = if (uiState.sortBy != SortOption.RELEVANCE) uiState.sortBy.displayName else "Sort",
                        isSelected = uiState.sortBy != SortOption.RELEVANCE,
                        onClick = { showSortSheet = true }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val results = uiState.results) {
                is UiState.Idle -> {
                    // ─── EXPLORE DISCOVERY HUB (IDLE STATE) ───
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Pick a Feeling
                        item {
                            CraveSectionHeader(
                                title = "Pick a Feeling",
                                onSeeAll = null
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val moods = listOf("Comfort", "Bright", "Heat", "Plant")
                            val moodColors = listOf(
                                GagYellow.copy(alpha = 0.2f),
                                GagBlue.copy(alpha = 0.2f),
                                GagPink.copy(alpha = 0.2f),
                                GagGreen.copy(alpha = 0.2f)
                            )
                            
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(moods.size) { index ->
                                    val mood = moods[index]
                                    Box(
                                        modifier = Modifier
                                            .width(100.dp)
                                            .height(80.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(moodColors[index])
                                            .clickable {
                                                when (mood) {
                                                    "Comfort" -> { viewModel.onQueryChanged(""); viewModel.onCategorySelected("Fast Food") }
                                                    "Bright" -> { viewModel.onQueryChanged(""); viewModel.onCategorySelected("Beverages") }
                                                    "Heat" -> { viewModel.onQueryChanged(""); viewModel.onCategorySelected("North Indian") }
                                                    "Plant" -> { viewModel.onQueryChanged(""); viewModel.onVegFilterChanged(true); viewModel.search() }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = mood,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Categories
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            CraveSectionHeader(
                                title = "Categories",
                                onSeeAll = null
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (uiState.categories is UiState.Success) {
                                val cats = (uiState.categories as UiState.Success).data
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(cats) { category ->
                                        GagCategoryChip(
                                            label = category.name,
                                            isSelected = uiState.selectedCategory == category.name,
                                            onClick = { viewModel.onCategorySelected(category.name) },
                                            iconUrl = category.imageUrl,
                                            emoji = category.emoji
                                        )
                                    }
                                }
                            } else if (uiState.categories is UiState.Loading) {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(5) {
                                        Box(
                                            modifier = Modifier
                                                .width(72.dp)
                                                .height(96.dp)
                                                .clip(RoundedCornerShape(36.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        )
                                    }
                                }
                            }
                        }

                        // 3. CAMPUS FAVORITES
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column {
                                Text(
                                    text = "CAMPUS FAVORITES",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Popular around campus",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        when (uiState.campusFavorites) {
                            is UiState.Loading -> {
                                items(2) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(180.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(180.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        )
                                    }
                                }
                            }
                            is UiState.Success -> {
                                val favs = (uiState.campusFavorites as UiState.Success).data
                                items(favs.chunked(2), key = { row -> "fav_${row.first().id}" }) { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        FoodItemCard(
                                            foodItem = pair[0],
                                            onClick = { onFoodClick(pair[0].id) },
                                            onAddToCart = { viewModel.onAddToCartClicked(pair[0]) },
                                            onFavoriteToggle = { viewModel.toggleFavorite(pair[0].id) },
                                            isFavorite = pair[0].isFavorite,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (pair.size > 1) {
                                            FoodItemCard(
                                                foodItem = pair[1],
                                                onClick = { onFoodClick(pair[1].id) },
                                                onAddToCart = { viewModel.onAddToCartClicked(pair[1]) },
                                                onFavoriteToggle = { viewModel.toggleFavorite(pair[1].id) },
                                                isFavorite = pair[1].isFavorite,
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            is UiState.Empty -> {
                                item {
                                    Text(
                                        text = "No popular picks yet. Try searching for a dish or category.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is UiState.Error -> {
                                item {
                                    Text(
                                        text = "Could not load campus favorites.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GagError
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }
                is UiState.Loading -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(4) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
                is UiState.Empty -> {
                    GagEmptyScreen(
                        title = "No bites found",
                        message = "Try another dish, category, or feeling.",
                        icon = Icons.Outlined.SearchOff,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is UiState.Error -> {
                    GagErrorScreen(
                        message = "Couldn't load the menu. Try again.", 
                        onRetry = viewModel::search, 
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is UiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            val headerText = if (uiState.query.isNotEmpty() && uiState.selectedCategory != null) {
                                "Results for \"${uiState.query}\" in ${uiState.selectedCategory}"
                            } else if (uiState.query.isNotEmpty()) {
                                "Results for \"${uiState.query}\""
                            } else if (uiState.selectedCategory != null) {
                                "Browsing ${uiState.selectedCategory}"
                            } else {
                                "${results.data.size} items found"
                            }
    
                            Text(
                                text = headerText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(results.data, key = { it.id }) { food ->
                            FoodItemCard(
                                foodItem = food,
                                onClick = { onFoodClick(food.id) },
                                onAddToCart = { viewModel.onAddToCartClicked(food) },
                                onFavoriteToggle = { viewModel.toggleFavorite(food.id) },
                                isFavorite = food.isFavorite
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheets
    if (showVegSheet) {
        ModalBottomSheet(
            onDismissRequest = { showVegSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text("Food Type", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                FilterOptionRow(label = "All", isSelected = uiState.filterVegOnly == null) {
                    viewModel.onVegFilterChanged(null)
                    viewModel.search()
                    showVegSheet = false
                }
                FilterOptionRow(label = "Vegetarian", isSelected = uiState.filterVegOnly == true) {
                    viewModel.onVegFilterChanged(true)
                    viewModel.search()
                    showVegSheet = false
                }
                FilterOptionRow(label = "Non-Vegetarian", isSelected = uiState.filterVegOnly == false) {
                    viewModel.onVegFilterChanged(false)
                    viewModel.search()
                    showVegSheet = false
                }
            }
        }
    }

    if (showPriceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPriceSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text("Price", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                FilterOptionRow(label = "Any price", isSelected = uiState.filterMaxPrice == null) {
                    viewModel.onMaxPriceChanged(null)
                    viewModel.search()
                    showPriceSheet = false
                }
                FilterOptionRow(label = "Under ₹50", isSelected = uiState.filterMaxPrice == 50.0) {
                    viewModel.onMaxPriceChanged(50.0)
                    viewModel.search()
                    showPriceSheet = false
                }
                FilterOptionRow(label = "Under ₹100", isSelected = uiState.filterMaxPrice == 100.0) {
                    viewModel.onMaxPriceChanged(100.0)
                    viewModel.search()
                    showPriceSheet = false
                }
                FilterOptionRow(label = "Under ₹150", isSelected = uiState.filterMaxPrice == 150.0) {
                    viewModel.onMaxPriceChanged(150.0)
                    viewModel.search()
                    showPriceSheet = false
                }
                FilterOptionRow(label = "Under ₹200", isSelected = uiState.filterMaxPrice == 200.0) {
                    viewModel.onMaxPriceChanged(200.0)
                    viewModel.search()
                    showPriceSheet = false
                }
            }
        }
    }

    if (showCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text("Category", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                FilterOptionRow(label = "All Categories", isSelected = uiState.selectedCategory == null) {
                    viewModel.onCategorySelected(null)
                    showCategorySheet = false
                }
                
                if (uiState.categories is UiState.Success) {
                    val cats = (uiState.categories as UiState.Success).data
                    cats.forEach { cat ->
                        FilterOptionRow(label = "${cat.emoji} ${cat.name}", isSelected = uiState.selectedCategory == cat.name) {
                            viewModel.onCategorySelected(cat.name)
                            showCategorySheet = false
                        }
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text("Sort By", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                SortOption.values().forEach { sort ->
                    FilterOptionRow(label = sort.displayName, isSelected = uiState.sortBy == sort) {
                        viewModel.onSortChanged(sort)
                        viewModel.search()
                        showSortSheet = false
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label, 
            style = MaterialTheme.typography.bodyLarge, 
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) GagPink else MaterialTheme.colorScheme.onSurface
        )
        if (isSelected) {
            Icon(Icons.Default.Check, "Selected", tint = GagPink, modifier = Modifier.size(20.dp))
        }
    }
}

private val SortOption.displayName: String get() = when (this) {
    SortOption.RELEVANCE -> "Relevance"
    SortOption.PRICE_LOW_TO_HIGH -> "Price: Low to High"
    SortOption.PRICE_HIGH_TO_LOW -> "Price: High to Low"
    SortOption.RATING -> "Rating"
    SortOption.PREP_TIME -> "Prep Time"
}
