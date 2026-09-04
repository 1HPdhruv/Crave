package com.srmfood.gag.feature.cart

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagEmptyScreen
import com.srmfood.gag.core.ui.component.GagFoodImage
import com.srmfood.gag.core.ui.component.GagPrimaryButton
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.component.QuantitySelector
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.Cart
import com.srmfood.gag.domain.model.CartItem
import com.srmfood.gag.domain.usecase.cart.ClearCartUseCase
import com.srmfood.gag.domain.usecase.cart.GetCartUseCase
import com.srmfood.gag.domain.usecase.cart.RemoveCartItemUseCase
import com.srmfood.gag.domain.usecase.cart.UpdateCartItemUseCase
import com.srmfood.gag.domain.usecase.cart.SyncCartUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────
@HiltViewModel
class CartViewModel @Inject constructor(
    private val getCartUseCase: GetCartUseCase,
    private val syncCartUseCase: SyncCartUseCase,
    private val updateCartItemUseCase: UpdateCartItemUseCase,
    private val removeCartItemUseCase: RemoveCartItemUseCase,
    private val clearCartUseCase: ClearCartUseCase
) : ViewModel() {

    private val _cart = MutableStateFlow<Cart?>(null)
    val cart: StateFlow<Cart?> = _cart.asStateFlow()

    init {
        viewModelScope.launch {
            syncCartUseCase()
            getCartUseCase().collectLatest { _cart.value = it }
        }
    }

    fun updateQuantity(itemId: String, qty: Int) {
        viewModelScope.launch { updateCartItemUseCase(itemId, qty) }
    }

    fun removeItem(itemId: String) {
        viewModelScope.launch { removeCartItemUseCase(itemId) }
    }

    fun clearCart() {
        viewModelScope.launch { clearCartUseCase() }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun CartScreen(
    onBack: () -> Unit,
    onCheckout: () -> Unit,
    onBrowseFood: () -> Unit,
    viewModel: CartViewModel = hiltViewModel()
) {
    val cart by viewModel.cart.collectAsState()
    val fmt = NumberFormat.getInstance(Locale("en", "IN"))

    Scaffold(
        containerColor = GagBackground,
        topBar = {
            GagTopBar(
                title = "My Cart",
                onBack = onBack,
                actions = {
                    if (cart != null && !cart!!.isEmpty) {
                        TextButton(onClick = viewModel::clearCart) {
                            Text("Clear", color = GagError, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (cart != null && !cart!!.isEmpty) {
                Surface(color = GagBackground, shadowElevation = 12.dp) {
                    Column(modifier = Modifier.padding(20.dp).navigationBarsPadding()) {
                        // Price breakdown
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)
                            Text("₹${fmt.format(cart!!.subtotal)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("GST (5%)", style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)
                            Text("₹${fmt.format(cart!!.tax)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = GagOutlineVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("₹${fmt.format(cart!!.total)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GagOrange)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        GagPrimaryButton(
                            text = "Proceed to Checkout",
                            onClick = onCheckout
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (cart == null || cart!!.isEmpty) {
            GagEmptyScreen(
                title = "Your cart is empty",
                message = "Browse our outlets and add something delicious!",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Outlet info
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = GagSurfaceVariant) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Ordering from:", style = MaterialTheme.typography.labelMedium, color = GagOnSurfaceVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cart!!.outletName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = GagOrange)
                        }
                    }
                }

                // Cart items
                items(cart!!.items, key = { it.id }) { item ->
                    CartItemRow(
                        item = item,
                        onQuantityChanged = { viewModel.updateQuantity(item.id, it) },
                        onRemove = { viewModel.removeItem(item.id) }
                    )
                }

                // Estimated prep time
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = GagSurfaceVariant) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Estimated prep time", style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)
                            Text("~${cart!!.estimatedPrepMinutes} minutes", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = GagAmber)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartItemRow(
    item: CartItem,
    onQuantityChanged: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Surface(shape = RoundedCornerShape(14.dp), color = GagSurface, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(GagSurfaceVariant)) {
                GagFoodImage(
                    model = item.foodImageUrl,
                    contentDescription = item.foodName,
                    modifier = Modifier.matchParentSize()
                )
                Box(modifier = Modifier.padding(4.dp).size(12.dp).background(if (item.isVeg) VegGreen else NonVegRed, CircleShape).align(Alignment.TopStart))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.foodName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (item.selectedCustomizations.isNotEmpty()) {
                    item.selectedCustomizations.forEach { custom ->
                        val priceText = if (custom.extraPrice > 0) " (+₹${custom.extraPrice.toInt()})" else ""
                        Text(
                            text = "${custom.optionName}$priceText", // Note: The schema might not provide the name, so in production we may need to fetch it or store it locally.
                            style = MaterialTheme.typography.bodySmall,
                            color = GagOnSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text("₹${item.price.toInt()} × ${item.quantity}", style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                Text("₹${item.itemTotal.toInt()}", style = MaterialTheme.typography.labelMedium, color = GagOrange, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "Remove", tint = GagError, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                QuantitySelector(
                    quantity = item.quantity,
                    onDecrease = { onQuantityChanged(item.quantity - 1) },
                    onIncrease = { onQuantityChanged(item.quantity + 1) },
                    minQuantity = 1
                )
            }
        }
    }
}
