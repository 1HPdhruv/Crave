package com.srmfood.gag.feature.outlets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.LocationOn
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
import com.srmfood.gag.core.ui.component.DeliveryModeSelector
import com.srmfood.gag.core.ui.component.FoodItemCard
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.HostelAddressBanner
import com.srmfood.gag.core.ui.component.HostelAddressDialog
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.model.Outlet
import com.srmfood.gag.domain.repository.HostelAddress
import com.srmfood.gag.domain.repository.OrderingMode
import com.srmfood.gag.domain.repository.OrderingModeRepository
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── State ────────────────────────────────────────────────────────────────────
data class OutletDetailUiState(
    val outlet: UiState<Outlet> = UiState.Loading,
    val menu: UiState<List<FoodItem>> = UiState.Loading,
    val selectedCategory: String? = null,
    val orderingMode: OrderingMode = OrderingMode.PICKUP,
    val hostelAddress: HostelAddress = HostelAddress()
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
    private val addToCartUseCase: AddToCartUseCase,
    private val orderingModeRepository: OrderingModeRepository
) : ViewModel() {

    private val outletId: String = savedStateHandle[Screen.OutletDetail.ARG_OUTLET_ID] ?: ""
    private val _uiState = MutableStateFlow(OutletDetailUiState())
    val uiState: StateFlow<OutletDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OutletDetailUiEvent>()
    val events: SharedFlow<OutletDetailUiEvent> = _events.asSharedFlow()

    init {
        loadOutletData()
        viewModelScope.launch {
            orderingModeRepository.orderingMode.collectLatest { mode ->
                _uiState.value = _uiState.value.copy(orderingMode = mode)
            }
        }
        viewModelScope.launch {
            orderingModeRepository.hostelAddress.collectLatest { address ->
                _uiState.value = _uiState.value.copy(hostelAddress = address)
            }
        }
    }

    fun setOrderingMode(mode: OrderingMode) {
        viewModelScope.launch { orderingModeRepository.setOrderingMode(mode) }
    }

    fun saveHostelAddress(address: HostelAddress) {
        viewModelScope.launch { orderingModeRepository.setHostelAddress(address) }
    }

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
            
            // Create Popular section if requested (only when All is selected)
            val popularItems = menuItems.filter { it.isPopular }
            
            val filteredMenu = if (uiState.selectedCategory != null)
                menuItems.filter { it.category == uiState.selectedCategory }
            else menuItems

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
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
                                    .height(380.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = outlet.imageUrl,
                                    contentDescription = outlet.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Top gradient for controls
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.5f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                                // Bottom gradient to blend into content
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.background
                                                )
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
                                    .offset(y = (-40).dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.background,
                                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                                    )
                                    .padding(horizontal = 24.dp, vertical = 24.dp)
                            ) {
                                Text(
                                    text = outlet.name,
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 2,
                                    lineHeight = MaterialTheme.typography.headlineLarge.lineHeight
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Location Context
                                val locationText = buildString {
                                    append(outlet.location.building)
                                    if (outlet.location.floor.isNotEmpty()) append(", ${outlet.location.floor}")
                                }
                                
                                if (locationText.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.LocationOn, 
                                            contentDescription = null, 
                                            tint = GagPink, 
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = locationText,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = GagPink,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                
                                // Metadata row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Open/Closed Status
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (outlet.isOpen) GagSuccessContainer else GagErrorContainer
                                    ) {
                                        Text(
                                            text = if (outlet.isOpen) "Open" else "Closed",
                                            color = if (outlet.isOpen) GagSuccess else GagError,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                    
                                    // Rating
                                    if (outlet.rating > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = GagYellow.copy(alpha = 0.15f)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Filled.Star, null, tint = GagYellow, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "${outlet.rating} (${outlet.totalReviews})",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = GagYellow
                                                )
                                            }
                                        }
                                    }
                                    
                                    // Wait Time
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Outlined.AccessTime, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${outlet.estimatedWaitMinutes} min wait",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (outlet.description.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = outlet.description,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                                    )
                                }
                            }
                        }

                        // 2.5 Delivery / Pickup Selector
                        item {
                            var showAddressDialog by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp)
                                    .offset(y = (-32).dp)
                            ) {
                                DeliveryModeSelector(
                                    selectedMode = uiState.orderingMode,
                                    onModeSelected = { viewModel.setOrderingMode(it) }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                HostelAddressBanner(
                                    address = uiState.hostelAddress,
                                    outletName = outlet.name,
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

                        // 3. Sticky Category Nav
                        if (categories.isNotEmpty()) {
                            stickyHeader {
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    modifier = Modifier.fillMaxWidth().offset(y = (-32).dp)
                                ) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                            .padding(horizontal = 24.dp, vertical = 8.dp)
                                            .offset(y = (-32).dp)
                                            .clip(RoundedCornerShape(20.dp))
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
                                                .padding(vertical = 48.dp)
                                                .offset(y = (-32).dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.RestaurantMenu,
                                                contentDescription = null,
                                                modifier = Modifier.size(56.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "No dishes available",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    // Optional: Popular Section if viewing "All"
                                    if (uiState.selectedCategory == null && popularItems.isNotEmpty()) {
                                        item {
                                            Text(
                                                text = "Popular Choices",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).offset(y = (-32).dp)
                                            )
                                        }
                                        items(popularItems, key = { "pop_${it.id}" }) { food ->
                                            FoodItemCard(
                                                foodItem = food,
                                                onClick = { onFoodClick(food.id) },
                                                onAddToCart = { viewModel.addToCart(food) },
                                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).offset(y = (-32).dp)
                                            )
                                        }
                                        item {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "All Menu",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).offset(y = (-32).dp)
                                            )
                                        }
                                    }
                                    
                                    items(filteredMenu, key = { it.id }) { food ->
                                        FoodItemCard(
                                            foodItem = food,
                                            onClick = { onFoodClick(food.id) },
                                            onAddToCart = { viewModel.addToCart(food) },
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).offset(y = (-32).dp)
                                        )
                                    }
                                    item { Spacer(modifier = Modifier.height(48.dp)) }
                                }
                            }
                            else -> {}
                        }
                    }
                    
                    // 5. Floating Top Controls (Back and Cart)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TopControlButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = onBack,
                            contentDescription = "Go back"
                        )
                        TopControlButton(
                            icon = Icons.Outlined.ShoppingCart,
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
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
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
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .then(
                if (!selected) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
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
