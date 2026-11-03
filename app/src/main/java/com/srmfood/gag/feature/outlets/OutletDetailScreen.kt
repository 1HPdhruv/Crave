package com.srmfood.gag.feature.outlets

import com.srmfood.gag.domain.usecase.outlet.studentMessage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import com.srmfood.gag.core.ui.component.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.RestaurantMenu
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
import com.srmfood.gag.core.ui.component.GagCategoryChip
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
    val reviews: UiState<com.srmfood.gag.domain.model.OutletReviewPage> = UiState.Loading,
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
    private val orderingModeRepository: OrderingModeRepository,
    private val reviewRepository: com.srmfood.gag.domain.repository.ReviewRepository
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
        viewModelScope.launch {
            val reviewsResult = reviewRepository.getOutletReviews(outletId = outletId, limit = 5, offset = 0)
            _uiState.value = _uiState.value.copy(
                reviews = reviewsResult.fold(
                    onSuccess = { if (it.reviews.isEmpty()) UiState.Empty else UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "Failed to load reviews") }
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
                _events.emit(OutletDetailUiEvent.ShowSnackbar(
                    if (it is com.srmfood.gag.domain.usecase.outlet.OutletUnavailableException) it.message ?: "This outlet can't take orders right now."
                    else "Couldn't add to cart: " + com.srmfood.gag.core.common.UiState.Error.userFacing(it.message ?: "please try again")
                ))
                // The outlet may have been suspended since this screen loaded: refresh it.
                if (it is com.srmfood.gag.domain.usecase.outlet.OutletUnavailableException) loadOutletData()
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
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 17.dp,
                            end = 17.dp,
                            bottom = padding.calculateBottomPadding() + 16.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Cover — tone block, ink border, hard shadow (website `.menu-cover`)
                        item(span = { GridItemSpan(2) }) {
                            val coverShape = RoundedCornerShape(28.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(top = 64.dp, end = 4.dp, bottom = 5.dp)
                                    .height(210.dp)
                                    .craveShadow(coverShape, 4.dp, 5.dp)
                                    .clip(coverShape)
                                    .background(craveToneFor(outlet.id))
                                    .border(2.dp, CraveInk, coverShape)
                            ) {
                                if (!outlet.imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = outlet.imageUrl,
                                        contentDescription = outlet.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text("🍴", fontSize = 90.sp, modifier = Modifier.align(Alignment.Center))
                                }
                            }
                        }

                        // 2. Outlet Information
                        item(span = { GridItemSpan(2) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 18.dp, bottom = 8.dp)
                            ) {
                                CraveStateBadge(
                                    text = when {
                                        !outlet.acceptsOrders -> "Not taking orders"
                                        outlet.isOpen -> "Open now"
                                        else -> "Closed right now"
                                    },
                                    container = if (outlet.acceptsOrders && outlet.isOpen) CraveChartreuse else CraveRedSoft
                                )
                                if (!outlet.acceptsOrders) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(shape = RoundedCornerShape(12.dp), color = GagErrorContainer) {
                                        Text(
                                            // is_active=false with an ACTIVE ordering status is also unavailable
                                            (if (outlet.orderingStatus.acceptsOrders) com.srmfood.gag.domain.model.OutletOrderingStatus.DEACTIVATED
                                             else outlet.orderingStatus).studentMessage(outlet.name),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GagError,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = outlet.name,
                                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 38.sp, lineHeight = 38.sp),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 2
                                )

                                val locationText = buildString {
                                    append(outlet.location.building)
                                    if (outlet.location.floor.isNotEmpty()) append(", ${outlet.location.floor}")
                                }
                                if (outlet.description.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = outlet.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (outlet.rating > 0) {
                                        Text("★ ${"%.1f".format(outlet.rating)} (${outlet.totalReviews})", style = GagMonoStyle.copy(fontSize = 11.sp))
                                    }
                                    Text("${outlet.estimatedWaitMinutes} min wait", style = GagMonoStyle.copy(fontSize = 11.sp))
                                }
                                if (locationText.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.LocationOn, null, tint = CraveOrangeDeep, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(locationText, style = GagMonoStyle.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (!outlet.isOpen && outlet.acceptsOrders) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "You can still pre-order for a later slot.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 2.5 Delivery / Pickup Selector
                        item(span = { GridItemSpan(2) }) {
                            var showAddressDialog by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
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

                        // 3. Category Nav
                        if (categories.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    LazyRow(
                                        contentPadding = PaddingValues(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        item {
                                            GagCategoryChip(
                                                label = "All",
                                                isSelected = uiState.selectedCategory == null,
                                                onClick = { viewModel.onCategorySelected(null) }
                                            )
                                        }
                                        items(categories) { cat ->
                                            GagCategoryChip(
                                                label = cat,
                                                isSelected = uiState.selectedCategory == cat,
                                                onClick = { viewModel.onCategorySelected(cat) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Menu Items
                        when (uiState.menu) {
                            is UiState.Loading -> {
                                items(4, span = { GridItemSpan(1) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    )
                                }
                            }
                            is UiState.Success -> {
                                if (filteredMenu.isEmpty()) {
                                    item(span = { GridItemSpan(2) }) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 48.dp)
                                                ,
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
                                        item(span = { GridItemSpan(2) }) {
                                            CraveHeading(
                                                eyebrow = "Fan favourites",
                                                title = "Popular choices",
                                                modifier = Modifier.padding(vertical = 12.dp)
                                            )
                                        }
                                        items(popularItems, key = { "pop_${it.id}" }) { food ->
                                            FoodItemCard(
                                                foodItem = food,
                                                onClick = { onFoodClick(food.id) },
                                                onAddToCart = { viewModel.addToCart(food) },
                                                modifier = Modifier
                                            )
                                        }
                                        item(span = { GridItemSpan(2) }) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            CraveHeading(
                                                eyebrow = "Everything",
                                                title = "Full menu",
                                                modifier = Modifier.padding(vertical = 12.dp)
                                            )
                                        }
                                    }
                                    
                                    items(filteredMenu, key = { it.id }) { food ->
                                        FoodItemCard(
                                            foodItem = food,
                                            onClick = { onFoodClick(food.id) },
                                            onAddToCart = { viewModel.addToCart(food) },
                                            modifier = Modifier
                                        )
                                    }
                                }
                            }
                            else -> {}
                        }

                        // 5. Reviews Section
                        item(span = { GridItemSpan(2) }) {
                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            CraveHeading(
                                eyebrow = "What people say",
                                title = "Reviews",
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            when (val reviewsState = uiState.reviews) {
                                is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                                is UiState.Empty -> {
                                    Text(
                                        text = "No reviews yet.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 32.dp)
                                    )
                                }
                                is UiState.Error -> {
                                    Text("Failed to load reviews.", color = GagError)
                                }
                                is UiState.Success -> {
                                    val reviews = reviewsState.data.reviews
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        reviews.forEach { review ->
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    repeat(5) { starIndex ->
                                                        Icon(
                                                            Icons.Filled.Star,
                                                            contentDescription = null,
                                                            tint = if (starIndex < review.rating) GagStarYellow else MaterialTheme.colorScheme.outline,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                                if (!review.reviewText.isNullOrBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "\"${review.reviewText}\"",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "${review.studentName} · ${review.createdAt}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                
                                                if (review.vendorReply != null) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = CraveLavender.copy(alpha = 0.45f),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(modifier = Modifier.padding(12.dp)) {
                                                            Text("Vendor reply:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = CraveOrangeDeep)
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(review.vendorReply.replyText, style = MaterialTheme.typography.bodySmall)
                                                        }
                                                    }
                                                }
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                                else -> {}
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                    
                    // 6. Floating Top Controls (Back and Cart)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 17.dp, vertical = 12.dp),
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
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CraveWhite)
            .border(1.dp, CraveInk, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = CraveInk,
            modifier = Modifier.size(22.dp)
        )
    }
}
