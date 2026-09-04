package com.srmfood.gag.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Schedule
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
        if (uiState.addedToCart) { viewModel.resetAddedToCart(); onCartClick() }
    }

    when (val foodState = uiState.food) {
        is UiState.Loading -> GagLoadingScreen()
        is UiState.Error -> GagErrorScreen(message = foodState.message, onRetry = {})
        is UiState.Success -> {
            val food = foodState.data
            Scaffold(
                containerColor = GagBackground,
                topBar = {
                    GagTopBar(
                        title = "",
                        onBack = onBack,
                        actions = {
                            IconButton(onClick = viewModel::toggleFavorite) {
                                Icon(
                                    imageVector = if (uiState.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favourite",
                                    tint = if (uiState.isFavorite) GagOrange else GagOnBackground
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    Surface(color = GagBackground, shadowElevation = 8.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
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
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // Hero image
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(280.dp).background(GagSurfaceVariant)) {
                            GagFoodImage(
                                model = food.imageUrl,
                                contentDescription = food.name,
                                category = food.category,
                                modifier = Modifier.matchParentSize()
                            )
                            // Veg indicator
                            Box(
                                modifier = Modifier.padding(16.dp).size(22.dp).background(Color.White, CircleShape).align(Alignment.TopEnd),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.FiberManualRecord,
                                    contentDescription = if (food.isVeg) "Veg" else "Non-veg",
                                    tint = if (food.isVeg) VegGreen else NonVegRed,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Food info
                    item {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(food.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Text(food.outletName, style = MaterialTheme.typography.bodyMedium, color = GagOrange)
                                }
                                Text("₹${food.price.toInt()}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = GagOrange)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Schedule, null, tint = GagOnSurfaceVariant, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${food.prepTimeMinutes} min", style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                                }
                                if (food.rating > 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Star, null, tint = GagAmber, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("${food.rating} (${food.totalReviews} reviews)", style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Description", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(food.description, style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)

                            if (food.ingredients.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Ingredients", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(food.ingredients.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                            }

                            if (food.calories != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("~${food.calories} cal", style = MaterialTheme.typography.labelSmall, color = GagOnSurfaceVariant)
                            }

                            if (!food.isAvailable) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Surface(shape = RoundedCornerShape(10.dp), color = GagErrorContainer, modifier = Modifier.fillMaxWidth()) {
                                    Text("Currently Unavailable", color = GagError, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
                                }
                            }
                        }
                    }

                    // Variants / Customizations
                    if (food.customizations.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = GagSurfaceVariant)
                        }
                        
                        items(food.customizations) { customization ->
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = customization.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = GagOnBackground
                                    )
                                    if (customization.isRequired) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = GagOrangeContainer
                                        ) {
                                            Text(
                                                text = "Required",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = GagOrange,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                
                                if (customization.maxSelections > 1) {
                                    Text(
                                        text = "Select up to ${customization.maxSelections}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GagOnSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                
                                val selectedOptionIds = uiState.selectedOptions[customization.id] ?: emptyList()
                                
                                customization.options.forEach { option ->
                                    val isSelected = selectedOptionIds.contains(option.id)
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (customization.maxSelections == 1) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = { viewModel.toggleOption(customization.id, option.id, customization.maxSelections) },
                                                    colors = RadioButtonDefaults.colors(selectedColor = GagOrange)
                                                )
                                            } else {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { viewModel.toggleOption(customization.id, option.id, customization.maxSelections) },
                                                    colors = CheckboxDefaults.colors(checkedColor = GagOrange)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = option.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = GagOnBackground
                                            )
                                        }
                                        
                                        if (option.extraPrice > 0) {
                                            Text(
                                                text = "+₹${option.extraPrice.toInt()}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = GagOnSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = GagSurfaceVariant)
                        }
                    }
                }
            }
        }
        else -> {}
    }
}
