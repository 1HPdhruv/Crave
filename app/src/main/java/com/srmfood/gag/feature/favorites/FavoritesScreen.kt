package com.srmfood.gag.feature.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.FoodItemCard
import com.srmfood.gag.core.ui.component.GagEmptyScreen
import com.srmfood.gag.core.ui.component.GagTopBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import com.srmfood.gag.core.ui.theme.GagOnSurfaceVariant
import com.srmfood.gag.core.ui.theme.GagOrange
import com.srmfood.gag.core.ui.theme.GagSurface

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onFoodClick: (String) -> Unit,
    onNavigateBottom: (String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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

    Scaffold(topBar = { GagTopBar("Favorites", onBack = onBack) }) { padding ->
        when (val state = uiState.results) {
            is UiState.Idle,
            is UiState.Empty -> {
                GagEmptyScreen(title = "No favorites yet", modifier = Modifier.padding(padding))
            }
            is UiState.Loading -> {
                // Could show a loading indicator, but keeping it simple like the empty screen
                GagEmptyScreen(title = "Loading...", modifier = Modifier.padding(padding))
            }
            is UiState.Error -> {
                GagEmptyScreen(title = "Error", message = state.message, modifier = Modifier.padding(padding))
            }
            is UiState.Success -> {
                val items = state.data
                if (items.isEmpty()) {
                    GagEmptyScreen(title = "No favorites yet", modifier = Modifier.padding(padding))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
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
