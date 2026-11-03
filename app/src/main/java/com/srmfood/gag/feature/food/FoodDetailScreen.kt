package com.srmfood.gag.feature.food

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import com.srmfood.gag.core.ui.component.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.component.GagFoodImage
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagPrimaryButton
import com.srmfood.gag.core.ui.component.QuantitySelector
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.usecase.cart.AddToCartUseCase
import com.srmfood.gag.domain.usecase.cart.ClearCartUseCase
import com.srmfood.gag.domain.usecase.cart.GetCartOutletIdUseCase
import com.srmfood.gag.domain.usecase.food.GetFoodItemUseCase
import com.srmfood.gag.domain.usecase.food.ToggleFavoriteUseCase
import com.srmfood.gag.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Events ───────────────────────────────────────────────────────────────────
sealed class FoodDetailUiEvent {
    data class ShowSnackbar(val message: String) : FoodDetailUiEvent()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────
data class FoodDetailUiState(
    val food: UiState<FoodItem> = UiState.Loading,
    val quantity: Int = 1,
    val isFavorite: Boolean = false,
    val addedToCart: Boolean = false,
    val selectedOptions: Map<String, List<String>> = emptyMap(),
    val showMixedOutletDialog: Boolean = false,
    val reviewsState: UiState<com.srmfood.gag.domain.model.ReviewPage> = UiState.Loading
)

@HiltViewModel
class FoodDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getFoodItemUseCase: GetFoodItemUseCase,
    private val addToCartUseCase: AddToCartUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val getCartOutletIdUseCase: GetCartOutletIdUseCase,
    private val clearCartUseCase: ClearCartUseCase,
    private val reviewRepository: com.srmfood.gag.domain.repository.ReviewRepository
) : ViewModel() {

    private val foodId: String = savedStateHandle[Screen.FoodDetail.ARG_FOOD_ID] ?: ""
    private val _uiState = MutableStateFlow(FoodDetailUiState())
    val uiState: StateFlow<FoodDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FoodDetailUiEvent>()
    val events: SharedFlow<FoodDetailUiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            loadFoodAndReviews()
        }
    }

    fun loadFoodAndReviews() {
        viewModelScope.launch {
            val result = getFoodItemUseCase(foodId)
            _uiState.update { state -> 
                state.copy(
                    food = result.fold(
                        onSuccess = { 
                            state.copy(isFavorite = it.isFavorite).let { _ -> UiState.Success(it) } 
                        }, 
                        onFailure = { UiState.Error(it.message ?: "Failed") }
                    ),
                    isFavorite = result.getOrNull()?.isFavorite ?: false
                )
            }
            
            _uiState.update { it.copy(reviewsState = UiState.Loading) }
            val reviewsResult = reviewRepository.getFoodReviews(foodId, limit = 20, offset = 0)
            _uiState.update { state ->
                state.copy(
                    reviewsState = reviewsResult.fold(
                        onSuccess = { UiState.Success(it) },
                        onFailure = { UiState.Error("Reviews are temporarily unavailable.") }
                    )
                )
            }
        }
    }

    fun increaseQuantity() {
        if (_uiState.value.quantity < 10) _uiState.update { it.copy(quantity = it.quantity + 1) }
    }

    fun decreaseQuantity() {
        if (_uiState.value.quantity > 1) _uiState.update { it.copy(quantity = it.quantity - 1) }
    }

    fun toggleOption(variantId: String, optionId: String, maxSelections: Int) {
        val currentSelections = _uiState.value.selectedOptions.toMutableMap()
        val currentOptionsForVariant = currentSelections[variantId]?.toMutableList() ?: mutableListOf()

        if (currentOptionsForVariant.contains(optionId)) {
            currentOptionsForVariant.remove(optionId)
        } else {
            if (maxSelections == 1) currentOptionsForVariant.clear()
            if (currentOptionsForVariant.size < maxSelections) currentOptionsForVariant.add(optionId)
        }
        
        if (currentOptionsForVariant.isEmpty()) {
            currentSelections.remove(variantId)
        } else {
            currentSelections[variantId] = currentOptionsForVariant
        }
        
        _uiState.update { it.copy(selectedOptions = currentSelections) }
    }

    fun computedPrice(): Double {
        val food = (_uiState.value.food as? UiState.Success)?.data ?: return 0.0
        var price = food.price
        
        for ((variantId, optionIds) in _uiState.value.selectedOptions) {
            val variant = food.customizations.find { it.id == variantId } ?: continue
            for (optionId in optionIds) {
                val option = variant.options.find { it.id == optionId } ?: continue
                price += option.extraPrice
            }
        }
        return price
    }

    fun isCartEnabled(): Boolean {
        val foodItem = (_uiState.value.food as? UiState.Success)?.data ?: return false
        if (!foodItem.isAvailable) return false
        
        for (customization in foodItem.customizations) {
            if (customization.isRequired) {
                val selectedCount = _uiState.value.selectedOptions[customization.id]?.size ?: 0
                if (selectedCount == 0) return false
            }
        }
        return true
    }

    fun addToCart() {
        val food = (_uiState.value.food as? UiState.Success)?.data ?: return
        if (!isCartEnabled()) return

        viewModelScope.launch {
            val cartOutletId = getCartOutletIdUseCase()
            if (cartOutletId != null && cartOutletId != food.outletId) {
                _uiState.update { it.copy(showMixedOutletDialog = true) }
            } else {
                executeAddToCart(food)
            }
        }
    }

    private suspend fun executeAddToCart(food: FoodItem) {
        val selectedCustomizationsList = mutableListOf<com.srmfood.gag.domain.model.SelectedCustomization>()
        for ((variantId, optionIds) in _uiState.value.selectedOptions) {
            val variant = food.customizations.find { it.id == variantId } ?: continue
            for (optionId in optionIds) {
                val option = variant.options.find { it.id == optionId } ?: continue
                selectedCustomizationsList.add(
                    com.srmfood.gag.domain.model.SelectedCustomization(
                        customizationId = variant.id,
                        customizationName = variant.name,
                        optionId = option.id,
                        optionName = option.name,
                        extraPrice = option.extraPrice
                    )
                )
            }
        }
        
        val result = addToCartUseCase(food, _uiState.value.quantity, selectedCustomizationsList)
        result.onSuccess {
            _events.emit(FoodDetailUiEvent.ShowSnackbar("Added to cart!"))
            _uiState.update { it.copy(addedToCart = true) }
        }.onFailure {
            _events.emit(FoodDetailUiEvent.ShowSnackbar(com.srmfood.gag.core.common.UiState.Error.userFacing(it.message ?: "Failed to add to cart")))
        }
    }

    fun dismissMixedOutletDialog() = _uiState.update { it.copy(showMixedOutletDialog = false) }

    fun onClearAndAddCart() {
        viewModelScope.launch {
            clearCartUseCase().onSuccess {
                val food = (_uiState.value.food as? UiState.Success)?.data
                if (food != null) executeAddToCart(food)
            }
            dismissMixedOutletDialog()
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            toggleFavoriteUseCase(foodId).onSuccess { isNow ->
                _uiState.update { it.copy(isFavorite = isNow) }
            }
        }
    }

    fun resetAddedToCart() = _uiState.update { it.copy(addedToCart = false) }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDetailScreen(
    onBack: () -> Unit,
    onCartClick: () -> Unit,
    viewModel: FoodDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FoodDetailUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LaunchedEffect(uiState.addedToCart) {
        if (uiState.addedToCart) { 
            viewModel.resetAddedToCart()
            onCartClick() 
        }
    }

    if (uiState.showMixedOutletDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMixedOutletDialog,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Different Outlet", fontWeight = FontWeight.Bold) },
            text = { Text("Your cart contains items from a different outlet. Clear cart and add this instead?") },
            confirmButton = {
                Button(
                    onClick = viewModel::onClearAndAddCart,
                    colors = ButtonDefaults.buttonColors(containerColor = CraveOrange, contentColor = CraveInk),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CraveInk),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Clear & Add") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMixedOutletDialog) { 
                    Text("Keep Cart", color = MaterialTheme.colorScheme.onSurfaceVariant) 
                }
            }
        )
    }

    when (val foodState = uiState.food) {
        is UiState.Loading -> GagLoadingScreen()
        is UiState.Error -> GagErrorScreen(message = foodState.message, onRetry = viewModel::loadFoodAndReviews)
        is UiState.Success -> {
            val food = foodState.data
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CraveInk)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 17.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            QuantitySelector(
                                quantity = uiState.quantity,
                                onDecrease = viewModel::decreaseQuantity,
                                onIncrease = viewModel::increaseQuantity
                            )
                            GagPrimaryButton(
                                text = "Add to bag  ·  ₹${(viewModel.computedPrice() * uiState.quantity).toInt()}",
                                onClick = viewModel::addToCart,
                                enabled = viewModel.isCartEnabled(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = padding.calculateBottomPadding())
                    ) {
                        // 1. HERO art — tone block, ink border, hard shadow
                        item {
                            val heroShape = RoundedCornerShape(28.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(start = 17.dp, end = 21.dp, top = 68.dp, bottom = 5.dp)
                                    .height(300.dp)
                                    .craveShadow(heroShape, 4.dp, 5.dp)
                                    .clip(heroShape)
                                    .background(craveToneFor(food.id))
                                    .border(2.dp, CraveInk, heroShape)
                            ) {
                                GagFoodImage(
                                    model = food.imageUrl,
                                    contentDescription = food.name,
                                    category = food.category,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // 2. Food Info
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 17.dp)
                                    .padding(top = 22.dp, bottom = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CraveSticker(
                                        food.category.ifBlank { "campus classic" },
                                        container = CraveOrange,
                                        content = CraveInk
                                    )
                                    if (food.rating > 0) {
                                        Text(
                                            text = "★ ${"%.1f".format(food.rating)} (${food.totalReviews})",
                                            style = GagMonoStyle.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = food.name,
                                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 38.sp, lineHeight = 38.sp),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                if (food.outletName.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "From ${food.outletName}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = CraveOrangeDeep,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DetailPill(
                                        text = if (food.isVeg) "Veg" else "Non-veg",
                                        container = if (food.isVeg) CraveChartreuse else CraveRedSoft,
                                        leading = {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(if (food.isVeg) VegGreen else NonVegRed)
                                            )
                                        }
                                    )
                                    DetailPill(
                                        text = "${food.prepTimeMinutes} min",
                                        container = MaterialTheme.colorScheme.surface,
                                        leading = { Icon(Icons.Outlined.Schedule, null, tint = CraveInk, modifier = Modifier.size(13.dp)) }
                                    )
                                    CraveMono(
                                        text = "₹${food.price.toInt()}",
                                        fontSize = 16f,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }

                                if (food.description.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = food.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (!food.isAvailable) {
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = CraveRedSoft,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, GagError),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Currently unavailable",
                                            color = GagError,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(14.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
                            }
                        }

                        // 3. Customizations
                        items(food.customizations) { customization ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 17.dp)
                                    .padding(top = 18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CraveEyebrow(customization.name, color = MaterialTheme.colorScheme.onSurface)
                                    if (customization.isRequired) {
                                        CraveStateBadge("Required", container = CraveOrange)
                                    } else if (customization.maxSelections > 1) {
                                        CraveEyebrow("up to ${customization.maxSelections}")
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                val selectedOptionIds = uiState.selectedOptions[customization.id] ?: emptyList()
                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    customization.options.forEach { option ->
                                        val isSelected = selectedOptionIds.contains(option.id)
                                        val optionShape = RoundedCornerShape(8.dp)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(optionShape)
                                                .background(if (isSelected) CraveChartreuse else MaterialTheme.colorScheme.surface)
                                                .border(1.dp, if (isSelected) CraveInk else MaterialTheme.colorScheme.outline, optionShape)
                                                .clickable { viewModel.toggleOption(customization.id, option.id, customization.maxSelections) }
                                                .padding(horizontal = 11.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(if (customization.maxSelections == 1) CircleShape else RoundedCornerShape(5.dp))
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) CraveInk else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        if (customization.maxSelections == 1) CircleShape else RoundedCornerShape(5.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) Text("✓", color = CraveInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = option.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) CraveInk else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (option.extraPrice > 0) {
                                                Text(
                                                    text = "+₹${option.extraPrice.toInt()}",
                                                    style = GagMonoStyle.copy(fontSize = 10.sp),
                                                    color = if (isSelected) CraveInk else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Reviews Section
                        item {
                            FoodReviewsSection(
                                rating = food.rating.toFloat(),
                                totalReviews = food.totalReviews,
                                reviewsState = uiState.reviewsState
                            )
                        }
                    }

                    // 5. Floating Top Controls
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
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Animated Heart Button
                            val scale by animateFloatAsState(
                                targetValue = if (uiState.isFavorite) 1.2f else 1.0f,
                                animationSpec = tween(durationMillis = 300),
                                label = "heart_scale"
                            )
                            val tint by animateColorAsState(
                                targetValue = if (uiState.isFavorite) CraveOrange else CraveInk,
                                animationSpec = tween(durationMillis = 300),
                                label = "heart_color"
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CraveWhite)
                                    .border(1.dp, CraveInk, RoundedCornerShape(10.dp))
                                    .clickable(onClick = viewModel::toggleFavorite),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (uiState.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = tint,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .scale(if (uiState.isFavorite) scale else 1.0f)
                                )
                            }
                            
                            TopControlButton(
                                icon = Icons.Outlined.ShoppingCart,
                                onClick = onCartClick,
                                contentDescription = "Cart"
                            )
                        }
                    }
                }
            }
        }
        is UiState.Empty, is UiState.Idle -> {}
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

@Composable
private fun DetailPill(
    text: String,
    container: Color,
    leading: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .border(1.dp, CraveInk, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, style = GagEyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.2.sp), color = CraveInk)
    }
}

@Composable
private fun FoodReviewsSection(
    rating: Float,
    totalReviews: Int,
    reviewsState: UiState<com.srmfood.gag.domain.model.ReviewPage>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp, vertical = 24.dp)
    ) {
        CraveHeading(eyebrow = "What people say", title = "Reviews")
        Spacer(modifier = Modifier.height(16.dp))

        // Rating summary
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (totalReviews > 0) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "%.1f".format(rating),
                                style = GagMonoStyle.copy(fontSize = 26.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Row {
                                repeat(5) { index ->
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = if (index < rating.toInt()) GagStarYellow else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$totalReviews reviews",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "No reviews yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (reviewsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CraveOrange)
                }
            }
            is UiState.Error -> {
                Text(
                    text = reviewsState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GagError,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            is UiState.Success -> {
                val reviews = reviewsState.data.reviews
                if (reviews.isEmpty()) {
                    Text(
                        text = "Be the first to review this item after ordering!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        reviews.forEach { review ->
                            ReviewItem(review)
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun ReviewItem(review: com.srmfood.gag.domain.model.FoodReview) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = review.studentName.ifBlank { "Student" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row {
                        repeat(5) { index ->
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (index < review.rating) GagStarYellow else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Text(
                    text = formatTimeAgo(review.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (review.reviewText.isNullOrBlank()) {
                Text(
                    text = "Rated without a written review.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            } else {
                Text(
                    text = review.reviewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
            }
            
            review.vendorReply?.let { reply ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CraveLavender.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        CraveEyebrow("Vendor reply", color = CraveInk)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = reply.replyText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimeAgo(dateString: String): String {
    return try {
        val instant = java.time.Instant.parse(dateString)
        val now = java.time.Instant.now()
        val duration = java.time.Duration.between(instant, now)
        when {
            duration.toDays() > 365 -> "${duration.toDays() / 365} years ago"
            duration.toDays() > 30 -> "${duration.toDays() / 30} months ago"
            duration.toDays() > 0 -> "${duration.toDays()} days ago"
            duration.toHours() > 0 -> "${duration.toHours()} hours ago"
            duration.toMinutes() > 0 -> "${duration.toMinutes()} mins ago"
            else -> "Just now"
        }
    } catch (e: Exception) {
        "Recently"
    }
}

