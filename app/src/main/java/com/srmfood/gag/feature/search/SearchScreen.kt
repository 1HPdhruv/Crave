package com.srmfood.gag.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.FoodItemCard
import com.srmfood.gag.core.ui.component.GagEmptyScreen
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagPrimaryButton
import com.srmfood.gag.core.ui.component.GagSecondaryButton
import com.srmfood.gag.core.ui.component.ShimmerBox
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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
            containerColor = GagSurface,
            title = { Text("Different Outlet", fontWeight = FontWeight.Bold) },
            text = { Text("Your cart contains items from a different outlet. Clear cart and add from this outlet?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onClearAndAddCart()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GagOrange)
                ) { Text("Clear & Add") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMixedOutletDialog) { Text("Keep Cart", color = GagOnSurfaceVariant) }
            }
        )
    }

    Scaffold(
        containerColor = GagBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                // Search field row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = GagOnBackground)
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = GagSurface
                    ) {
                        OutlinedTextField(
                            value = uiState.query,
                            onValueChange = viewModel::onQueryChanged,
                            placeholder = { Text("Search momos, biryani, pizza…", color = GagOnSurfaceVariant) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = GagOnSurfaceVariant, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (uiState.query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                        Icon(Icons.Default.Close, "Clear", tint = GagOnSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { viewModel.search(); keyboard?.hide() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GagOrange,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = GagSurface,
                                unfocusedContainerColor = GagSurface,
                                cursorColor = GagOrange
                            ),
                            modifier = Modifier.focusRequester(focusRequester)
                        )
                    }
                    IconButton(onClick = viewModel::toggleFilters) {
                        Icon(Icons.Default.FilterList, "Filters",
                            tint = if (uiState.showFilters) GagOrange else GagOnSurfaceVariant)
                    }
                }

                // Filters panel
                AnimatedVisibility(visible = uiState.showFilters, enter = expandVertically(), exit = shrinkVertically()) {
                    FilterPanel(
                        currentVeg = uiState.filterVegOnly,
                        currentSort = uiState.sortBy,
                        onVegChanged = viewModel::onVegFilterChanged,
                        onSortChanged = viewModel::onSortChanged,
                        onApply = { viewModel.search() }
                    )
                }

                HorizontalDivider(color = GagOutlineVariant, thickness = 1.dp)
            }
        }
    ) { padding ->
        when (val results = uiState.results) {
            is UiState.Idle -> {
                // Show hint
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, null, tint = GagOnSurfaceVariant.copy(0.4f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Search across all SRM outlets", style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)
                    }
                }
            }
            is UiState.Loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            is UiState.Empty -> GagEmptyScreen(
                title = "No results found",
                message = "Try a different search term or remove filters",
                icon = Icons.Outlined.SearchOff,
                modifier = Modifier.padding(padding)
            )
            is UiState.Error -> GagErrorScreen(message = results.message, onRetry = viewModel::search, modifier = Modifier.padding(padding))
            is UiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "${results.data.size} results for \"${uiState.query}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = GagOnSurfaceVariant
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

@Composable
private fun FilterPanel(
    currentVeg: Boolean?,
    currentSort: SortOption,
    onVegChanged: (Boolean?) -> Unit,
    onSortChanged: (SortOption) -> Unit,
    onApply: () -> Unit
) {
    Surface(color = GagSurface) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Filters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            // Veg filter
            Text("Food Type", style = MaterialTheme.typography.labelMedium, color = GagOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = currentVeg == null,
                        onClick = { onVegChanged(null) },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GagOrange, selectedLabelColor = Color.White)
                    )
                }
                item {
                    FilterChip(
                        selected = currentVeg == true,
                        onClick = { onVegChanged(true) },
                        label = { Text("🟢 Veg") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GagSuccess, selectedLabelColor = Color.White)
                    )
                }
                item {
                    FilterChip(
                        selected = currentVeg == false,
                        onClick = { onVegChanged(false) },
                        label = { Text("🔴 Non-Veg") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GagError, selectedLabelColor = Color.White)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Sort By", style = MaterialTheme.typography.labelMedium, color = GagOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SortOption.values().toList()) { sort ->
                    FilterChip(
                        selected = currentSort == sort,
                        onClick = { onSortChanged(sort) },
                        label = { Text(sort.displayName) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GagOrange, selectedLabelColor = Color.White)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onApply,
                colors = ButtonDefaults.buttonColors(containerColor = GagOrange),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Apply Filters") }
        }
    }
}

private val SortOption.displayName: String get() = when (this) {
    SortOption.RELEVANCE -> "Relevance"
    SortOption.PRICE_LOW_TO_HIGH -> "Price ↑"
    SortOption.PRICE_HIGH_TO_LOW -> "Price ↓"
    SortOption.RATING -> "Rating"
    SortOption.PREP_TIME -> "Prep Time"
}
