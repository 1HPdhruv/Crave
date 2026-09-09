package com.srmfood.gag.feature.cart

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.outlined.DeleteOutline
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (cart != null && !cart!!.isEmpty) {
                Surface(
                    color = GagBackground, 
                    shadowElevation = 16.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .navigationBarsPadding()
                    ) {
                        GagPrimaryButton(
                            text = "Proceed to Checkout   ₹${fmt.format(cart!!.total)}",
                            onClick = onCheckout,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (cart == null || cart!!.isEmpty) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Your cart is empty",
                    style = MaterialTheme.typography.headlineMedium, 
                    fontWeight = FontWeight.ExtraBold, 
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Looks like you haven't added anything yet.", 
                    style = MaterialTheme.typography.bodyLarge, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                GagPrimaryButton(
                    text = "Explore Food",
                    onClick = onBrowseFood
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)
            ) {
                // Header
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).statusBarsPadding()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                            }
                            TextButton(onClick = viewModel::clearCart, modifier = Modifier.offset(x = 12.dp)) {
                                Text("Clear Cart", color = GagError, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Your Cart", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${cart!!.items.size} items", style = MaterialTheme.typography.titleMedium, color = GagPink)
                    }
                }

                // Outlet info
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp), 
                        color = GagPinkContainer
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Ordering from", style = MaterialTheme.typography.bodyMedium, color = GagOnPinkContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cart!!.outletName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GagPink)
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

                // Bill Summary
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Bill Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Subtotal", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("₹${fmt.format(cart!!.subtotal)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("GST (5%)", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("₹${fmt.format(cart!!.tax)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                                Text("₹${fmt.format(cart!!.total)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = GagPink)
                            }

                            if (cart!!.estimatedPrepMinutes > 0) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = GagYellow.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "Estimated prep time: ~${cart!!.estimatedPrepMinutes} mins",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = GagYellow,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
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
    Surface(
        shape = RoundedCornerShape(20.dp), 
        color = MaterialTheme.colorScheme.surface, 
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // Image
                Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(GagSurfaceVariant)) {
                    GagFoodImage(
                        model = item.foodImageUrl,
                        contentDescription = item.foodName,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(modifier = Modifier.padding(6.dp).size(12.dp).background(if (item.isVeg) GagSuccess else GagError, CircleShape).align(Alignment.TopStart))
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.foodName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("₹${item.price.toInt()}", style = MaterialTheme.typography.titleSmall, color = GagPink, fontWeight = FontWeight.SemiBold)
                    
                    if (item.selectedCustomizations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        item.selectedCustomizations.forEach { custom ->
                            val priceText = if (custom.extraPrice > 0) " (+₹${custom.extraPrice.toInt()})" else ""
                            Text(
                                text = "• ${custom.optionName}$priceText",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Bottom Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuantitySelector(
                    quantity = item.quantity,
                    onDecrease = { onQuantityChanged(item.quantity - 1) },
                    onIncrease = { onQuantityChanged(item.quantity + 1) },
                    minQuantity = 1
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("₹${item.itemTotal.toInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(
                        onClick = onRemove, 
                        modifier = Modifier.size(36.dp).background(GagErrorContainer, CircleShape)
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, "Remove item", tint = GagError, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
