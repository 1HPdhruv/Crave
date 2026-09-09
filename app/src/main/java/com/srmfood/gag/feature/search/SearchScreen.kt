package com.srmfood.gag.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.srmfood.gag.core.ui.component.FoodItemCard
import com.srmfood.gag.core.ui.component.GagEmptyScreen
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.SortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
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
        if (uiState.selectedCategory == null && uiState.query.isEmpty()) {
            focusRequester.requestFocus()
        }
        viewModel.events.collect { event ->
            when (event) {
                is SearchUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    // Mixed outlet dialog
    if (uiState.showMixedOutletDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMixedOutletDialog,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Different Outlet", fontWeight = FontWeight.Bold) },
            text = { Text("Your cart contains items from a different outlet. Clear cart and add from this outlet?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.onClearAndAddCart() },
                    colors = ButtonDefaults.buttonColors(containerColor = CraveRed)
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
                // Header & Search
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    
                    Surface(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        OutlinedTextField(
                            value = uiState.query,
                            onValueChange = viewModel::onQueryChanged,
                            placeholder = { 
                                Text(
                                    text = if (uiState.selectedCategory != null) "Search in ${uiState.selectedCategory}…" else "Search momos, biryani, pizza…", 
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
                                cursorColor = CraveRed
                            ),
                            modifier = Modifier.focusRequester(focusRequester)
                        )
                    }
                }
                
                // Horizontal Filter Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Clear all
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

                    // Veg
                    CraveFilterChip(
                        label = when(uiState.filterVegOnly) {
                            true -> "Veg"
                            false -> "Non-Veg"
                            null -> "Food Type"
                        },
                        isSelected = uiState.filterVegOnly != null,
                        onClick = { showVegSheet = true }
                    )

                    // Price
                    CraveFilterChip(
                        label = if (uiState.filterMaxPrice != null) "Under ₹${uiState.filterMaxPrice!!.toInt()}" else "Price",
                        isSelected = uiState.filterMaxPrice != null,
                        onClick = { showPriceSheet = true }
                    )

                    // Category
                    CraveFilterChip(
                        label = uiState.selectedCategory ?: "Category",
                        isSelected = uiState.selectedCategory != null,
                        onClick = { showCategorySheet = true }
                    )

                    // Sort
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
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), 
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "What are you craving?", 
                            style = MaterialTheme.typography.titleLarge, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Search across all SRM outlets", 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        title = "No cravings found",
                        message = "Try a different food, category, or filter.",
                        icon = Icons.Outlined.SearchOff,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is UiState.Error -> {
                    GagErrorScreen(
                        message = results.message, 
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
            color = if (isSelected) CraveRed else MaterialTheme.colorScheme.onSurface
        )
        if (isSelected) {
            Icon(Icons.Default.Check, "Selected", tint = CraveRed, modifier = Modifier.size(20.dp))
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
