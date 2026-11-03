package com.srmfood.gag.feature.cart

import com.srmfood.gag.domain.usecase.outlet.studentMessage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.srmfood.gag.core.ui.component.*
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.text.withStyle
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

// ─── ViewModel ────────────────────────────────────────────────────
@HiltViewModel
class CartViewModel @Inject constructor(
    private val getCartUseCase: GetCartUseCase,
    private val syncCartUseCase: SyncCartUseCase,
    private val updateCartItemUseCase: UpdateCartItemUseCase,
    private val removeCartItemUseCase: RemoveCartItemUseCase,
    private val clearCartUseCase: ClearCartUseCase,
    private val checkOutletAvailability: com.srmfood.gag.domain.usecase.outlet.CheckOutletAvailabilityUseCase
) : ViewModel() {

    private val _cart = MutableStateFlow<Cart?>(null)
    val cart: StateFlow<Cart?> = _cart.asStateFlow()

    /** Non-null message while the cart's outlet is suspended/deactivated (checked against the server, not the cache). */
    private val _outletBlock = MutableStateFlow<String?>(null)
    val outletBlock: StateFlow<String?> = _outletBlock.asStateFlow()
    private var checkedOutletId: String? = null

    init {
        viewModelScope.launch {
            syncCartUseCase()
            getCartUseCase().collectLatest {
                _cart.value = it
                // Re-check whenever the cart's outlet changes; cart contents themselves are preserved.
                if (it != null && !it.isEmpty && it.outletId != checkedOutletId) refreshOutletAvailability()
                if (it == null || it.isEmpty) { checkedOutletId = null; _outletBlock.value = null }
            }
        }
    }

    /** Also called from the screen on resume so a suspension that happened while the app was idle is noticed. */
    fun refreshOutletAvailability() {
        val outletId = _cart.value?.outletId ?: return
        checkedOutletId = outletId
        viewModelScope.launch {
            _outletBlock.value = when (val a = checkOutletAvailability(outletId)) {
                is com.srmfood.gag.domain.usecase.outlet.OutletAvailability.Unavailable ->
                    a.status.studentMessage(a.outletName)
                is com.srmfood.gag.domain.usecase.outlet.OutletAvailability.Available -> null
            }
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

// ─── Screen ───────────────────────────────────────────────────────
@Composable
fun CartScreen(
    onBack: () -> Unit,
    onCheckout: () -> Unit,
    onBrowseFood: () -> Unit,
    viewModel: CartViewModel = hiltViewModel()
) {
    val cart by viewModel.cart.collectAsState()
    val outletBlock by viewModel.outletBlock.collectAsState()
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.refreshOutletAvailability()
        onPauseOrDispose { }
    }
    val fmt = NumberFormat.getInstance(Locale("en", "IN"))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val safeCart = cart
            if (safeCart != null && !safeCart.isEmpty) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 0.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CraveInk),
                    shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = GagSpacing.Large, vertical = 16.dp)
                            .navigationBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL",
                                style = GagEyebrowStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            CraveMono("₹${fmt.format(safeCart.total)}", fontSize = 18f)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        outletBlock?.let { msg ->
                            Text(msg, style = MaterialTheme.typography.bodySmall, color = GagError)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        GagPrimaryButton(text = "Checkout  ↗", onClick = onCheckout, enabled = outletBlock == null)
                    }
                }
            }
        }
    ) { padding ->
        val safeCart = cart
        if (safeCart == null || safeCart.isEmpty) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CraveBlob(size = 76.dp, container = CraveChartreuse) {
                    Icon(Icons.Outlined.ShoppingBag, null, tint = CraveInk, modifier = Modifier.size(34.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Your bag is empty",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Find something good on campus.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                GagPrimaryButton(text = "Explore food  ↗", onClick = onBrowseFood, modifier = Modifier.width(220.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp
                )
            ) {
                // Header
                item {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 17.dp, vertical = GagSpacing.Medium)
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier.clickableNoRipple(onBack),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("←", color = CraveOrange, fontSize = 19.sp)
                            Spacer(Modifier.width(7.dp))
                            Text("back", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        CraveEyebrow("Your bag")
                        Text(
                            text = androidx.compose.ui.text.buildAnnotatedString {
                                append("Ready to ")
                                withStyle(androidx.compose.ui.text.SpanStyle(color = CraveOrange)) { append("order?") }
                            },
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp, lineHeight = 40.sp),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        CraveMono(
                            text = "${safeCart.items.size} item${if (safeCart.items.size != 1) "s" else ""} in your bag",
                            fontSize = 10f,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Outlet context
                item {
                    CravePanel(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 4.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Storefront, null, tint = CraveOrangeDeep, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = safeCart.outletName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                CraveMono("SRM KTR", fontSize = 9f, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(GagSpacing.Medium))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 17.dp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Cart items
                items(safeCart.items, key = { it.id }) { item ->
                    OrbitCartItemRow(
                        item = item,
                        onQuantityChanged = { viewModel.updateQuantity(item.id, it) },
                        onRemove = { viewModel.removeItem(item.id) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 17.dp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Bill Summary (website `.checkout-summary`)
                item {
                    Spacer(modifier = Modifier.height(GagSpacing.Large))
                    CravePanel(
                        modifier = Modifier.padding(horizontal = 17.dp).fillMaxWidth(),
                        borderColor = CraveInk,
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CraveEyebrow("Order summary")
                            Spacer(Modifier.height(4.dp))
                            CravePriceRow("Item total", "₹${fmt.format(safeCart.subtotal)}")
                            CravePriceRow("Tax", "₹${fmt.format(safeCart.tax)}")
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            CravePriceRow("Total", "₹${fmt.format(safeCart.total)}", isTotal = true)
                        }
                    }
                    Spacer(modifier = Modifier.height(GagSpacing.Large))
                }
            }
        }
    }
}

@Composable
private fun OrbitCartItemRow(
    item: CartItem,
    onQuantityChanged: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Food image on tone block
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(craveToneFor(item.id))
                .border(1.dp, CraveInk, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (item.foodImageUrl.isNullOrBlank()) Text("🍽", fontSize = 36.sp)
            else AsyncImage(
                model = item.foodImageUrl,
                contentDescription = item.foodName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Name + customization
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.foodName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2
            )
            if (item.selectedCustomizations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.selectedCustomizations.joinToString(", ") { it.optionName },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            CraveMono("₹${item.itemTotal.toInt()}")
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Quantity controls
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            QuantitySelector(
                quantity = item.quantity,
                onIncrease = { onQuantityChanged(item.quantity + 1) },
                onDecrease = {
                    if (item.quantity > 1) onQuantityChanged(item.quantity - 1)
                    else onRemove()
                }
            )
        }
    }
}
