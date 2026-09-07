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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.foundation.border
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = padding.calculateBottomPadding())
                    ) {
                        // 1. Hero Section
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                            ) {
                                AsyncImage(
                                    model = outlet.imageUrl,
                                    contentDescription = outlet.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize()
                                )
                                // Gradient Overlay
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.3f),
                                                    GagBackground
                                                ),
                                                startY = 200f
                                            )
                                        )
                                )
                            }
                        }

                        // 2. Outlet Information
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .offset(y = (-32).dp)
                            ) {
                                Text(
                                    text = outlet.name,
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Metadata row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Rating
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Star, null, tint = GagYellow, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${outlet.rating} (${outlet.totalReviews})",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    // Wait Time
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.AccessTime, null, tint = GagInfo, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${outlet.estimatedWaitMinutes} min",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    // Open/Closed Status
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (outlet.isOpen) GagSuccessContainer else GagErrorContainer
                                    ) {
                                        Text(
                                            text = if (outlet.isOpen) "Open" else "Closed",
                                            color = if (outlet.isOpen) GagSuccess else GagError,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Text(
                                    text = outlet.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 3. Sticky Category Nav
                        if (categories.isNotEmpty()) {
                            stickyHeader {
                                Surface(
                                    color = GagBackground,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        item {
                                            GagCategoryChip(
                                                selected = uiState.selectedCategory == null,
                                                onClick = { viewModel.onCategorySelected(null) },
                                                label = "All"
                                            )
                                        }
                                        items(categories) { cat ->
                                            GagCategoryChip(
                                                selected = uiState.selectedCategory == cat,
                                                onClick = { viewModel.onCategorySelected(cat) },
                                                label = cat
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Menu Items
                        when (uiState.menu) {
                            is UiState.Loading -> {
                                items(3) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    )
                                }
                            }
                            is UiState.Success -> {
                                if (filteredMenu.isEmpty()) {
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 48.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Outlined.RestaurantMenu,
                                                contentDescription = null,
                                                modifier = Modifier.size(48.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "No dishes available",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    items(filteredMenu, key = { it.id }) { food ->
                                        FoodItemCard(
                                            foodItem = food,
                                            onClick = { onFoodClick(food.id) },
                                            onAddToCart = { viewModel.addToCart(food) },
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        )
                                    }
                                    item { Spacer(modifier = Modifier.height(32.dp)) }
                                }
                            }
                            else -> {}
                        }
                    }
                    
                    // 5. Top Controls (Back and Cart)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TopControlButton(
                            icon = androidx.compose.material.icons.Icons.Default.ArrowBack,
                            onClick = onBack,
                            contentDescription = "Go back"
                        )
                        TopControlButton(
                            icon = androidx.compose.material.icons.Icons.Outlined.ShoppingCart,
                            onClick = onCartClick,
                            contentDescription = "Cart"
                        )
                    }
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun TopControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color.White.copy(alpha = 0.9f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.Black,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun GagCategoryChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    val backgroundColor = if (selected) GagPink else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    
    Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .then(
                if (!selected) Modifier.border(1.dp, MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape)
                else Modifier
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}
