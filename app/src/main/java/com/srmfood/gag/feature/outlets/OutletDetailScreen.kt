package com.srmfood.gag.feature.outlets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.FoodItemCard
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.model.Outlet
import com.srmfood.gag.domain.model.QueueLevel
import com.srmfood.gag.domain.usecase.cart.AddToCartUseCase
import com.srmfood.gag.domain.usecase.food.GetMenuByOutletUseCase
import com.srmfood.gag.domain.usecase.outlet.GetOutletDetailsUseCase
import com.srmfood.gag.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── State ────────────────────────────────────────────────────────────────────
data class OutletDetailUiState(
    val outlet: UiState<Outlet> = UiState.Loading,
    val menu: UiState<List<FoodItem>> = UiState.Loading,
    val selectedCategory: String? = null
)

sealed class OutletDetailUiEvent {
    data class ShowSnackbar(val message: String) : OutletDetailUiEvent()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────
@HiltViewModel
class OutletDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getOutletDetailsUseCase: GetOutletDetailsUseCase,
    private val getMenuByOutletUseCase: GetMenuByOutletUseCase,
    private val addToCartUseCase: AddToCartUseCase
) : ViewModel() {

    private val outletId: String = savedStateHandle[Screen.OutletDetail.ARG_OUTLET_ID] ?: ""
    private val _uiState = MutableStateFlow(OutletDetailUiState())
    val uiState: StateFlow<OutletDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OutletDetailUiEvent>()
    val events: SharedFlow<OutletDetailUiEvent> = _events.asSharedFlow()

    init { loadOutletData() }

    private fun loadOutletData() {
        viewModelScope.launch {
            val outletResult = getOutletDetailsUseCase(outletId)
            _uiState.value = _uiState.value.copy(
                outlet = outletResult.fold(onSuccess = { UiState.Success(it) }, onFailure = { UiState.Error(it.message ?: "Failed") })
            )
        }
        viewModelScope.launch {
            val menuResult = getMenuByOutletUseCase(outletId)
            _uiState.value = _uiState.value.copy(
                menu = menuResult.fold(
                    onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "Failed") }
                )
            )
        }
    }

    fun onCategorySelected(category: String?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun addToCart(food: FoodItem) {
        viewModelScope.launch {
            addToCartUseCase(food, 1).onSuccess {
                _events.emit(OutletDetailUiEvent.ShowSnackbar("Added ${food.name} to cart"))
            }.onFailure {
                _events.emit(OutletDetailUiEvent.ShowSnackbar("Failed to add: ${it.message}"))
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun OutletDetailScreen(
    onBack: () -> Unit,
    onFoodClick: (String) -> Unit,
    onCartClick: () -> Unit,
    viewModel: OutletDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OutletDetailUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    when (val outletState = uiState.outlet) {
        is UiState.Loading -> GagLoadingScreen()
        is UiState.Error -> GagErrorScreen(message = outletState.message, onRetry = {})
        is UiState.Success -> {
            val outlet = outletState.data
            val menuItems = (uiState.menu as? UiState.Success)?.data ?: emptyList()
            val categories = menuItems.map { it.category }.distinct()
            val filteredMenu = if (uiState.selectedCategory != null)
                menuItems.filter { it.category == uiState.selectedCategory }
            else menuItems

            Scaffold(
                containerColor = GagBackground,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = { GagTopBar(title = outlet.name, onBack = onBack) }
            ) { padding ->
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // Outlet Hero Image
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                            AsyncImage(
                                model = outlet.imageUrl,
                                contentDescription = outlet.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                            Box(
                                modifier = Modifier.matchParentSize()
                                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, GagBackground.copy(0.9f))))
                            )
                            Column(
                                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (outlet.isOpen) GagSuccessContainer else GagErrorContainer
                                ) {
                                    Text(
                                        text = if (outlet.isOpen) "● Open" else "● Closed",
                                        color = if (outlet.isOpen) GagSuccess else GagError,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Outlet info cards
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val queueLevel = QueueLevel.fromQueueSize(outlet.currentQueueSize)
                            InfoChip(
                                icon = { Icon(Icons.Outlined.Groups, null, modifier = Modifier.size(14.dp), tint = GagAmber) },
                                text = "Queue: ${outlet.currentQueueSize}",
                                modifier = Modifier.weight(1f)
                            )
                            InfoChip(
                                icon = { Icon(Icons.Outlined.AccessTime, null, modifier = Modifier.size(14.dp), tint = GagInfo) },
                                text = "~${outlet.estimatedWaitMinutes}m wait",
                                modifier = Modifier.weight(1f)
                            )
                            InfoChip(
                                icon = { Icon(Icons.Filled.Star, null, modifier = Modifier.size(14.dp), tint = GagAmber) },
                                text = "${outlet.rating} (${outlet.totalReviews})",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Description
                    item {
                        Text(
                            outlet.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = GagOnSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${outlet.operatingHours.openTime} – ${outlet.operatingHours.closeTime}",
                            style = MaterialTheme.typography.labelSmall,
                            color = GagOnSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Category filter chips
                    if (categories.isNotEmpty()) {
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = uiState.selectedCategory == null,
                                        onClick = { viewModel.onCategorySelected(null) },
                                        label = { Text("All") },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GagOrange, selectedLabelColor = Color.White)
                                    )
                                }
                                items(categories) { cat ->
                                    FilterChip(
                                        selected = uiState.selectedCategory == cat,
                                        onClick = { viewModel.onCategorySelected(cat) },
                                        label = { Text(cat) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GagOrange, selectedLabelColor = Color.White)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Menu items
                    when (uiState.menu) {
                        is UiState.Loading -> item { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = GagOrange) } }
                        is UiState.Success -> {
                            items(filteredMenu, key = { it.id }) { food ->
                                FoodItemCard(
                                    foodItem = food,
                                    onClick = { onFoodClick(food.id) },
                                    onAddToCart = { viewModel.addToCart(food) },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                        else -> {}
                    }
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun InfoChip(icon: @Composable () -> Unit, text: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = GagSurface, modifier = modifier) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            icon()
            Spacer(modifier = Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = GagOnSurface)
        }
    }
}
