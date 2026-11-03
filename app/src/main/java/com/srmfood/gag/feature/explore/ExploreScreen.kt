package com.srmfood.gag.feature.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.FlowRow
import com.srmfood.gag.core.ui.component.*
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
                    colors = ButtonDefaults.buttonColors(containerColor = CraveOrange, contentColor = CraveInk),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CraveInk),
                    shape = RoundedCornerShape(10.dp)
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
                // Header (website PageHeading: eyebrow + "Find your bite.")
                Column(modifier = Modifier.fillMaxWidth().padding(start = 17.dp, end = 17.dp, top = 12.dp)) {
                    CraveEyebrow("Explore")
                    Text(
                        text = androidx.compose.ui.text.buildAnnotatedString {
                            append("Find your ")
                            withStyle(androidx.compose.ui.text.SpanStyle(color = CraveOrange)) { append("bite.") }
                        },
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 38.sp, lineHeight = 38.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Search field: white, 1px ink border
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChanged,
                    placeholder = {
                        Text(
                            text = "Biryani, dosa, cold coffee…",
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
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        viewModel.search()
                        keyboard?.hide()
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CraveInk,
                        unfocusedBorderColor = CraveInk,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        cursorColor = CraveOrange
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 17.dp, vertical = 12.dp)
                        .focusRequester(focusRequester)
                )

                // Horizontal Filter Row (Always visible)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 17.dp, vertical = 8.dp),
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val results = uiState.results) {
                is UiState.Idle -> {
                    // ─── EXPLORE DISCOVERY HUB (IDLE STATE) ───
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp)
                    ) {
                        // 1. Categories (wrap, like the website's category chips)
                        item {
                            CraveHeading(
                                eyebrow = "Start here",
                                title = "What’s the vibe?",
                                modifier = Modifier.padding(horizontal = 17.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            when (val cats = uiState.categories) {
                                is UiState.Success -> FlowRow(
                                    modifier = Modifier.padding(horizontal = 17.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    cats.data.forEach { category ->
                                        GagCategoryChip(
                                            label = category.name,
                                            isSelected = uiState.selectedCategory == category.name,
                                            onClick = { viewModel.onCategorySelected(category.name) },
                                            iconUrl = category.imageUrl,
                                            emoji = category.emoji
                                        )
                                    }
                                }
                                is UiState.Loading -> FlowRow(
                                    modifier = Modifier.padding(horizontal = 17.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    repeat(6) {
                                        GagLoadingSkeleton(Modifier.size(92.dp, 38.dp).clip(CircleShape))
                                    }
                                }
                                else -> {}
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                            CraveHeading(
                                eyebrow = "Campus favourites",
                                title = "Popular right now",
                                modifier = Modifier.padding(horizontal = 17.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 2. Campus favourites — 2 per row
                        when (val favs = uiState.campusFavorites) {
                            is UiState.Loading -> craveGridItems(List(4) { it }) {
                                GagLoadingSkeleton(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(16.dp)))
                            }
                            is UiState.Success -> craveGridItems(favs.data, key = { "fav_${it.id}" }) { food ->
                                FoodItemCard(
                                    foodItem = food,
                                    onClick = { onFoodClick(food.id) },
                                    onAddToCart = { viewModel.onAddToCartClicked(food) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(food.id) },
                                    isFavorite = food.isFavorite,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is UiState.Empty -> item {
                                Text(
                                    text = "No popular picks yet. Try searching for a dish or category.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 17.dp)
                                )
                            }
                            is UiState.Error -> item {
                                Column(modifier = Modifier.padding(horizontal = 17.dp)) {
                                    Text(
                                        text = "Could not load campus favorites.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GagError
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    GagSecondaryButton(text = "Try again", onClick = viewModel::search, modifier = Modifier.width(140.dp))
                                }
                            }
                            else -> {}
                        }
                    }
                }
                is UiState.Loading -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 20.dp)
                    ) {
                        craveGridItems(List(6) { it }) {
                            GagLoadingSkeleton(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(16.dp)))
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
                        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
                    ) {
                        item {
                            val headerText = if (uiState.query.isNotEmpty() && uiState.selectedCategory != null) {
                                "Results for \"${uiState.query}\" in ${uiState.selectedCategory}"
                            } else if (uiState.query.isNotEmpty()) {
                                "Results for \"${uiState.query}\""
                            } else if (uiState.selectedCategory != null) {
                                "Browsing ${uiState.selectedCategory}"
                            } else {
                                "${results.data.size} item${if (results.data.size == 1) "" else "s"} found"
                            }

                            Text(
                                text = headerText,
                                style = GagMonoStyle.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 17.dp).padding(bottom = 14.dp)
                            )
                        }
                        craveGridItems(results.data, key = { it.id }) { food ->
                            FoodItemCard(
                                foodItem = food,
                                onClick = { onFoodClick(food.id) },
                                onAddToCart = { viewModel.onAddToCartClicked(food) },
                                onFavoriteToggle = { viewModel.toggleFavorite(food.id) },
                                isFavorite = food.isFavorite,
                                modifier = Modifier.fillMaxWidth()
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
            color = MaterialTheme.colorScheme.onSurface
        )
        if (isSelected) {
            Box(
                modifier = Modifier.size(22.dp).clip(CircleShape).background(CraveChartreuse).border(1.dp, CraveInk, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, "Selected", tint = CraveInk, modifier = Modifier.size(14.dp))
            }
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
