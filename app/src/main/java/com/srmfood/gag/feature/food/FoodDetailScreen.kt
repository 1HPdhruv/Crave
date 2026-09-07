package com.srmfood.gag.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingCart
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
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.component.GagFoodImage
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagPrimaryButton
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.component.QuantitySelector
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.FoodItem
import com.srmfood.gag.domain.usecase.cart.AddToCartUseCase
import com.srmfood.gag.domain.usecase.food.GetFoodItemUseCase
import com.srmfood.gag.domain.usecase.food.ToggleFavoriteUseCase
import com.srmfood.gag.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class FoodDetailUiState(
    val food: UiState<FoodItem> = UiState.Loading,
    val quantity: Int = 1,
    val isFavorite: Boolean = false,
    val addedToCart: Boolean = false,
    val selectedOptions: Map<String, List<String>> = emptyMap() // Map of variantId -> list of optionIds
)

@HiltViewModel
class FoodDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getFoodItemUseCase: GetFoodItemUseCase,
    private val addToCartUseCase: AddToCartUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : ViewModel() {

    private val foodId: String = savedStateHandle[Screen.FoodDetail.ARG_FOOD_ID] ?: ""
    private val _uiState = MutableStateFlow(FoodDetailUiState())
    val uiState: StateFlow<FoodDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val result = getFoodItemUseCase(foodId)
            _uiState.value = _uiState.value.copy(
                food = result.fold(onSuccess = { UiState.Success(it) }, onFailure = { UiState.Error(it.message ?: "Failed") })
            )
        }
    }

    fun increaseQuantity() {
        if (_uiState.value.quantity < 10) _uiState.value = _uiState.value.copy(quantity = _uiState.value.quantity + 1)
    }

    fun decreaseQuantity() {
        if (_uiState.value.quantity > 1) _uiState.value = _uiState.value.copy(quantity = _uiState.value.quantity - 1)
    }

    fun toggleOption(variantId: String, optionId: String, maxSelections: Int) {
        val currentSelections = _uiState.value.selectedOptions.toMutableMap()
        val currentOptionsForVariant = currentSelections[variantId]?.toMutableList() ?: mutableListOf()

        if (currentOptionsForVariant.contains(optionId)) {
            currentOptionsForVariant.remove(optionId)
        } else {
            if (maxSelections == 1) {
                currentOptionsForVariant.clear()
            }
            if (currentOptionsForVariant.size < maxSelections) {
                currentOptionsForVariant.add(optionId)
            }
        }
        
        if (currentOptionsForVariant.isEmpty()) {
            currentSelections.remove(variantId)
        } else {
            currentSelections[variantId] = currentOptionsForVariant
        }
        
        _uiState.value = _uiState.value.copy(selectedOptions = currentSelections)
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
        
        viewModelScope.launch {
            addToCartUseCase(food, _uiState.value.quantity, selectedCustomizationsList)
            _uiState.value = _uiState.value.copy(addedToCart = true)
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val result = toggleFavoriteUseCase(foodId)
            result.onSuccess { isNow ->
                _uiState.value = _uiState.value.copy(isFavorite = isNow)
            }
        }
    }

    fun resetAddedToCart() { _uiState.value = _uiState.value.copy(addedToCart = false) }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun FoodDetailScreen(
    onBack: () -> Unit,
    onCartClick: () -> Unit,
    viewModel: FoodDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.addedToCart) {
        if (uiState.addedToCart) { 
            viewModel.resetAddedToCart()
            // Just show snackbar handled by AddToCartUseCase/events, or keep user here if no auto-nav requested.
            // The prompt says: "Do not automatically navigate away unless the existing behavior already does so. The user should be able to continue browsing."
            // Wait, the existing behavior was `onCartClick()`. So I will keep `onCartClick()`.
            // Let me look at previous implementation: `if (uiState.addedToCart) { viewModel.resetAddedToCart(); onCartClick() }`
            onCartClick() 
        }
    }

    when (val foodState = uiState.food) {
        is UiState.Loading -> GagLoadingScreen()
        is UiState.Error -> GagErrorScreen(message = foodState.message, onRetry = {})
        is UiState.Success -> {
            val food = foodState.data
            Scaffold(
                containerColor = GagBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    Surface(
                        color = GagBackground, 
                        shadowElevation = 16.dp,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                                .navigationBarsPadding(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            QuantitySelector(
                                quantity = uiState.quantity,
                                onDecrease = viewModel::decreaseQuantity,
                                onIncrease = viewModel::increaseQuantity
                            )
                            GagPrimaryButton(
                                text = "Add to Cart  ₹${(viewModel.computedPrice() * uiState.quantity).let { if (it % 1 == 0.0) it.toInt() else it }}",
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
                        // 1. Hero image
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                                    .background(GagSurfaceVariant)
                            ) {
                                GagFoodImage(
                                    model = food.imageUrl,
                                    contentDescription = food.name,
                                    category = food.category,
                                    modifier = Modifier.matchParentSize()
                                )
                                // Top-down subtle gradient to make icons visible
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.4f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                                // Bottom-up gradient to blend into content
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    GagBackground
                                                )
                                            )
                                        )
                                )
                            }
                        }

                        // 2. Food info
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = (-24).dp)
                                    .background(
                                        color = GagBackground, 
                                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                                    )
                                    .padding(horizontal = 20.dp, vertical = 24.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(), 
                                    horizontalArrangement = Arrangement.SpaceBetween, 
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = food.name, 
                                            style = MaterialTheme.typography.headlineMedium, 
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        if (food.outletName.isNotEmpty()) {
                                            Text(
                                                text = "From ${food.outletName}", 
                                                style = MaterialTheme.typography.bodyMedium, 
                                                color = GagPink,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    Text(
                                        text = "₹${food.price.toInt()}", 
                                        style = MaterialTheme.typography.headlineMedium, 
                                        fontWeight = FontWeight.ExtraBold, 
                                        color = GagPink
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Metadata row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Veg/Non-Veg
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (food.isVeg) GagSuccessContainer else GagErrorContainer
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.FiberManualRecord,
                                                contentDescription = if (food.isVeg) "Veg" else "Non-veg",
                                                tint = if (food.isVeg) GagSuccess else GagError,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (food.isVeg) "Veg" else "Non-Veg",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (food.isVeg) GagSuccess else GagError
                                            )
                                        }
                                    }
                                    
                                    // Prep Time
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${food.prepTimeMinutes} min", 
                                                style = MaterialTheme.typography.labelMedium, 
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    // Rating
                                    if (food.rating > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = GagYellow.copy(alpha = 0.2f)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Filled.Star, null, tint = GagYellow, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${food.rating} (${food.totalReviews})", 
                                                    style = MaterialTheme.typography.labelMedium, 
                                                    color = GagYellow,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                                
                                if (food.description.isNotEmpty()) {
                                    Text("Description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(food.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("No description available", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                }

                                if (food.ingredients.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Ingredients", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(food.ingredients.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                if (food.calories != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("🔥 ~${food.calories} cal", style = MaterialTheme.typography.labelLarge, color = GagOrange)
                                }

                                if (!food.isAvailable) {
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Surface(shape = RoundedCornerShape(12.dp), color = GagErrorContainer, modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "Currently Unavailable", 
                                            color = GagError, 
                                            style = MaterialTheme.typography.titleMedium, 
                                            fontWeight = FontWeight.Bold, 
                                            modifier = Modifier.padding(16.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Customizations
                        if (food.customizations.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Customize your order", 
                                    style = MaterialTheme.typography.headlineSmall, 
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                )
                            }
                            
                            items(food.customizations) { customization ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = customization.name,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (customization.isRequired) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = GagOrangeContainer
                                            ) {
                                                Text(
                                                    text = "Required",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = GagOrange,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                    
                                    if (customization.maxSelections > 1) {
                                        Text(
                                            text = "Select up to ${customization.maxSelections}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    
                                    val selectedOptionIds = uiState.selectedOptions[customization.id] ?: emptyList()
                                    
                                    customization.options.forEach { option ->
                                        val isSelected = selectedOptionIds.contains(option.id)
                                        
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { viewModel.toggleOption(customization.id, option.id, customization.maxSelections) }
                                                .padding(vertical = 12.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (customization.maxSelections == 1) {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = { viewModel.toggleOption(customization.id, option.id, customization.maxSelections) },
                                                        colors = RadioButtonDefaults.colors(
                                                            selectedColor = GagPink,
                                                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    )
                                                } else {
                                                    Checkbox(
                                                        checked = isSelected,
                                                        onCheckedChange = { viewModel.toggleOption(customization.id, option.id, customization.maxSelections) },
                                                        colors = CheckboxDefaults.colors(
                                                            checkedColor = GagPink,
                                                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            checkmarkColor = Color.White
                                                        )
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = option.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            
                                            if (option.extraPrice > 0) {
                                                Text(
                                                    text = "+₹${option.extraPrice.toInt()}",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = GagPink
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Floating Top Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TopControlButton(
                            icon = Icons.Default.ArrowBack,
                            onClick = onBack,
                            contentDescription = "Go back"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TopControlButton(
                                icon = if (uiState.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                onClick = viewModel::toggleFavorite,
                                contentDescription = "Favorite",
                                tint = if (uiState.isFavorite) GagPink else Color.Black
                            )
                            TopControlButton(
                                icon = Icons.Outlined.ShoppingCart,
                                onClick = onCartClick,
                                contentDescription = "Cart",
                                tint = Color.Black
                            )
                        }
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
    contentDescription: String,
    tint: Color = Color.Black
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.9f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
