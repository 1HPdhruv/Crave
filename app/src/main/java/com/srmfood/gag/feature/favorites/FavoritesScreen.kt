package com.srmfood.gag.feature.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.FoodItemCard
import com.srmfood.gag.core.ui.component.GagBottomNavBar
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagPrimaryButton
import com.srmfood.gag.core.ui.component.studentBottomNavItems
import com.srmfood.gag.core.ui.theme.*

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onFoodClick: (String) -> Unit,
    onNavigateBottom: (String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Mixed-outlet dialog — preserved exactly
    if (uiState.showMixedOutletDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMixedOutletDialog,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Different Outlet", fontWeight = FontWeight.ExtraBold) },
            text = { Text("Your cart contains items from a different outlet. Clear cart and add from this outlet?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.onClearAndAddCart() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GagPink)
                ) { Text("Clear & Add", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMixedOutletDialog) {
                    Text("Keep Cart", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            GagBottomNavBar(
                items = studentBottomNavItems,
                currentRoute = "favorites",
                onItemSelected = onNavigateBottom
            )
        },
        containerColor = GagBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        when (val state = uiState.results) {
            is UiState.Loading -> GagLoadingScreen(modifier = Modifier.padding(padding))

            is UiState.Error -> GagErrorScreen(
                message = state.message,
                onRetry = { /* ViewModel auto-retries via init */ },
                modifier = Modifier.padding(padding)
            )

            is UiState.Idle,
            is UiState.Empty -> FavoritesEmptyState(
                modifier = Modifier.padding(padding),
                onExplore = { onNavigateBottom("home") }
            )

            is UiState.Success -> {
                val items = state.data
                if (items.isEmpty()) {
                    FavoritesEmptyState(
                        modifier = Modifier.padding(padding),
                        onExplore = { onNavigateBottom("home") }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = padding.calculateTopPadding(),
                                bottom = padding.calculateBottomPadding()
                            )
                    ) {
                        // Header
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                                .statusBarsPadding()
                        ) {
                            Text(
                                "Your Favorites",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Your all-time cravings",
                                style = MaterialTheme.typography.titleMedium,
                                color = GagPink
                            )
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(items, key = { it.id }) { food ->
                                FoodItemCard(
                                    foodItem = food,
                                    onClick = { onFoodClick(food.id) },
                                    onAddToCart = { viewModel.onAddToCartClicked(food) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(food.id) },
                                    isFavorite = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesEmptyState(modifier: Modifier = Modifier, onExplore: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FavoriteBorder,
            contentDescription = null,
            tint = GagPink.copy(alpha = 0.4f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No favorites yet \uD83D\uDC97",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Save the food you never want to forget.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        GagPrimaryButton(
            text = "Explore Food",
            onClick = onExplore
        )
    }
}

