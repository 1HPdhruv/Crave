package com.srmfood.gag.feature.vendor.menu

import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagEmptyScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.GagBackground
import com.srmfood.gag.core.ui.theme.GagError
import com.srmfood.gag.core.ui.theme.GagOnSurfaceVariant
import com.srmfood.gag.core.ui.theme.GagSuccess
import com.srmfood.gag.core.ui.theme.GagSurface
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.usecase.food.GetVendorFoodItemsUseCase
import com.srmfood.gag.domain.usecase.food.UpdateFoodAvailabilityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VendorMenuViewModel @Inject constructor(
    private val getVendorFoodItemsUseCase: GetVendorFoodItemsUseCase,
    private val updateFoodAvailabilityUseCase: UpdateFoodAvailabilityUseCase
) : ViewModel() {

    private val _foodItems = MutableStateFlow<UiState<List<FoodItem>>>(UiState.Loading)
    val foodItems: StateFlow<UiState<List<FoodItem>>> = _foodItems.asStateFlow()

    init {
        loadMenu()
    }

    fun loadMenu() {
        viewModelScope.launch {
            _foodItems.value = UiState.Loading
            val res = getVendorFoodItemsUseCase()
            _foodItems.value = res.fold(
                onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Failed") }
            )
        }
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    fun toggleAvailability(foodId: String, currentVal: Boolean) {
        viewModelScope.launch {
            updateFoodAvailabilityUseCase(foodId, !currentVal)
                .onFailure { _message.value = "Couldn't change availability. Please try again." }
            loadMenu() // always show the server's truth
        }
    }
}

@Composable
fun VendorMenuScreen(
    onBack: () -> Unit,
    onAddItem: () -> Unit = {},
    onEditItem: (String) -> Unit = {},
    externalMessage: String? = null,
    onMessageShown: () -> Unit = {},
    viewModel: VendorMenuViewModel = hiltViewModel()
) {
    val state by viewModel.foodItems.collectAsState()
    val vmMessage by viewModel.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(externalMessage) { if (externalMessage != null) { viewModel.loadMenu(); snack.showSnackbar(externalMessage); onMessageShown() } }
    LaunchedEffect(vmMessage) { vmMessage?.let { snack.showSnackbar(it); viewModel.messageShown() } }

    Scaffold(
        topBar = { GagTopBar("Manage Menu", onBack = onBack) },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAddItem, text = { Text("Add item") }, icon = { Icon(androidx.compose.material.icons.Icons.Filled.Add, null) }) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (val uiState = state) {
            is UiState.Loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            is UiState.Empty -> GagEmptyScreen(title = "No menu items yet", message = "Tap “Add item” to create your first dish.", modifier = Modifier.padding(padding))
            is UiState.Error -> com.srmfood.gag.core.ui.component.GagErrorScreen(message = uiState.message, onRetry = viewModel::loadMenu, modifier = Modifier.padding(padding))
            is UiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.data, key = { it.id }) { food ->
                        VendorFoodCard(food = food, onClick = { onEditItem(food.id) }, onToggleAvailability = { viewModel.toggleAvailability(food.id, food.isAvailable) })
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun VendorFoodCard(food: FoodItem, onClick: () -> Unit, onToggleAvailability: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GagSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("₹${food.price.toInt()} · ${food.category}", style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                
                Spacer(modifier = Modifier.height(8.dp))
                val statusColor = if (food.isAvailable) GagSuccess else GagError
                Text(if (food.isAvailable) "Available" else "Out of Stock", style = MaterialTheme.typography.labelMedium, color = statusColor)
            }
            Switch(
                checked = food.isAvailable,
                onCheckedChange = { onToggleAvailability() },
                colors = SwitchDefaults.colors(checkedTrackColor = GagSuccess)
            )
        }
    }
}
