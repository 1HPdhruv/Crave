package com.srmfood.gag.feature.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagPrimaryButton
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.Cart
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.PaymentMethod
import com.srmfood.gag.domain.model.PaymentVerificationRequest
import com.srmfood.gag.domain.model.PickupSlot
import com.srmfood.gag.domain.model.RazorpayOrderDetails
import com.srmfood.gag.core.payment.RazorpayManager
import com.srmfood.gag.core.payment.RazorpayResult
import com.srmfood.gag.domain.repository.PaymentRepository
import com.srmfood.gag.domain.usecase.cart.GetCartUseCase
import com.srmfood.gag.domain.usecase.order.PlaceOrderUseCase
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
import com.razorpay.Checkout
import org.json.JSONObject
import android.app.Activity
import androidx.compose.ui.platform.LocalContext

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class CheckoutUiState(
    val cart: Cart? = null,
    val availableSlots: UiState<List<PickupSlot>> = UiState.Loading,
    val selectedSlot: PickupSlot? = null,
    val selectedPaymentMethod: PaymentMethod = PaymentMethod.ONLINE,
    val specialInstructions: String = "",
    val orderState: UiState<Order> = UiState.Idle,
    val razorpayOrderDetails: RazorpayOrderDetails? = null,
    val paymentVerificationState: UiState<Unit> = UiState.Idle
)

sealed class CheckoutUiEvent {
    data class OpenRazorpay(val details: RazorpayOrderDetails, val orderId: String) : CheckoutUiEvent()
    data class OrderSuccess(val orderId: String) : CheckoutUiEvent()
    data class ShowError(val message: String) : CheckoutUiEvent()
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val getCartUseCase: GetCartUseCase,
    private val placeOrderUseCase: PlaceOrderUseCase,
    private val cancelOrderUseCase: com.srmfood.gag.domain.usecase.order.CancelOrderUseCase,
    private val getPickupSlotsUseCase: com.srmfood.gag.domain.usecase.order.GetPickupSlotsUseCase,
    private val cartRepository: com.srmfood.gag.domain.repository.CartRepository,
    private val paymentRepository: PaymentRepository,
    private val razorpayManager: RazorpayManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CheckoutUiEvent>()
    val events: SharedFlow<CheckoutUiEvent> = _events.asSharedFlow()

    private var currentOrderId: String? = null
    private var hasLoadedSlots = false

    init {
        viewModelScope.launch {
            getCartUseCase().collectLatest { cart ->
                _uiState.value = _uiState.value.copy(cart = cart)
                
                // Fetch slots if we have an outlet and haven't fetched yet
                if (cart != null && !hasLoadedSlots) {
                    hasLoadedSlots = true
                    loadPickupSlots(cart.outletId)
                }
            }
        }

        viewModelScope.launch {
            razorpayManager.results.collect { result ->
                when (result) {
                    is RazorpayResult.Success -> {
                        val orderId = currentOrderId ?: return@collect
                        onRazorpaySuccess(
                            orderId = orderId,
                            rzpOrderId = result.data.orderId ?: "",
                            rzpPaymentId = result.data.paymentId ?: "",
                            rzpSignature = result.data.signature ?: ""
                        )
                    }
                    is RazorpayResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            orderState = UiState.Error("Payment failed: ${result.message}")
                        )
                    }
                }
            }
        }
    }

    fun loadPickupSlots(outletId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(availableSlots = UiState.Loading)
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val result = getPickupSlotsUseCase(outletId, today)
            
            _uiState.value = _uiState.value.copy(
                availableSlots = result.fold(
                    onSuccess = { slots -> 
                        if (slots.isEmpty()) UiState.Empty else UiState.Success(slots) 
                    },
                    onFailure = { UiState.Error(it.message ?: "Failed to load slots") }
                )
            )
        }
    }

    fun onSlotSelected(slot: PickupSlot) {
        _uiState.value = _uiState.value.copy(selectedSlot = slot)
    }

    fun onPaymentMethodSelected(method: PaymentMethod) {
        _uiState.value = _uiState.value.copy(selectedPaymentMethod = method)
    }

    fun onInstructionsChanged(text: String) {
        _uiState.value = _uiState.value.copy(specialInstructions = text)
    }

    fun placeOrder() {
        val state = _uiState.value
        val cart = state.cart ?: return
        val slot = state.selectedSlot ?: return

        viewModelScope.launch {
            // Prevent duplicate order lock by cancelling any previous incomplete order
            if (currentOrderId != null) {
                cancelOrderUseCase(currentOrderId!!, "Payment retried or cancelled by user")
                currentOrderId = null
            }

            _uiState.value = _uiState.value.copy(orderState = UiState.Loading)
            val result = placeOrderUseCase(
                outletId = cart.outletId,
                pickupSlotId = slot.id,
                paymentMethod = state.selectedPaymentMethod,
                specialInstructions = state.specialInstructions.ifBlank { null }
            )

            result.onSuccess { order ->
                currentOrderId = order.id
                if (state.selectedPaymentMethod == PaymentMethod.ONLINE) {
                    initiateRazorpay(order.id)
                } else {
                    _uiState.value = _uiState.value.copy(orderState = UiState.Success(order))
                    _events.emit(CheckoutUiEvent.OrderSuccess(order.id))
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(orderState = UiState.Error(error.message ?: "Failed"))
            }
        }
    }

    private suspend fun initiateRazorpay(orderId: String) {
        paymentRepository.createRazorpayOrder(orderId)
            .onSuccess { details ->
                _uiState.value = _uiState.value.copy(
                    orderState = UiState.Idle,
                    razorpayOrderDetails = details
                )
                _events.emit(CheckoutUiEvent.OpenRazorpay(details, orderId))
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(orderState = UiState.Error("Payment setup failed: ${error.message}"))
            }
    }

    fun onRazorpaySuccess(
        orderId: String,
        rzpOrderId: String,
        rzpPaymentId: String,
        rzpSignature: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(paymentVerificationState = UiState.Loading)
            val request = PaymentVerificationRequest(
                order_id = orderId,
                razorpay_order_id = rzpOrderId,
                razorpay_payment_id = rzpPaymentId,
                razorpay_signature = rzpSignature
            )
            
            paymentRepository.verifyRazorpayPayment(request)
                .onSuccess {
                    cartRepository.clearCart() // Local wipe only AFTER success
                    _uiState.value = _uiState.value.copy(paymentVerificationState = UiState.Success(Unit))
                    _events.emit(CheckoutUiEvent.OrderSuccess(orderId))
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(paymentVerificationState = UiState.Error(error.message ?: "Verification failed"))
                }
        }
    }

    fun resetOrderState() { _uiState.value = _uiState.value.copy(orderState = UiState.Idle) }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckoutScreen(
    onBack: () -> Unit,
    onOrderPlaced: (String) -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val fmt = NumberFormat.getInstance(Locale("en", "IN"))

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CheckoutUiEvent.OrderSuccess -> {
                    onOrderPlaced(event.orderId)
                }
                is CheckoutUiEvent.OpenRazorpay -> {
                    val activity = context as? Activity ?: return@collect
                    val co = Checkout()
                    co.setKeyID(event.details.key_id)
                    
                    try {
                        val options = JSONObject().apply {
                            put("name", "GaG SRM")
                            put("description", "Food Pre-order")
                            put("image", "https://s2.pstatp.com/static/img/logo.png")
                            put("order_id", event.details.razorpay_order_id)
                            put("amount", event.details.amount)
                            put("currency", event.details.currency)
                            put("prefill", JSONObject().apply {
                                put("email", uiState.cart?.outletName ?: "student@srm.edu")
                            })
                            put("theme", JSONObject().apply {
                                put("color", "#F44336") // GagOrange
                            })
                        }
                        co.open(activity, options)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                is CheckoutUiEvent.ShowError -> {
                    // Handled via state for now
                }
            }
        }
    }

    Scaffold(
        containerColor = GagBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (uiState.cart != null) {
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
                            text = if (uiState.selectedSlot == null) "Select Pickup Slot First" else "Proceed to Payment   ₹${fmt.format(uiState.cart!!.total)}",
                            onClick = viewModel::placeOrder,
                            enabled = uiState.selectedSlot != null && uiState.cart != null,
                            isLoading = uiState.orderState is UiState.Loading || uiState.paymentVerificationState is UiState.Loading,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    ) { padding ->
        val cart = uiState.cart
        if (cart == null) {
            GagLoadingScreen(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)
            ) {
                // Header
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).statusBarsPadding()) {
                        IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Checkout", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Almost there! Complete your order.", style = MaterialTheme.typography.titleMedium, color = GagPink)
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
                            Text("Pickup from", style = MaterialTheme.typography.bodyMedium, color = GagOnPinkContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cart.outletName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GagPink)
                        }
                    }
                }

                // Order summary
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    CheckoutSection(
                        title = "Order Summary",
                        action = { 
                            TextButton(onClick = onBack) { 
                                Text("Edit Cart", color = GagPink, fontWeight = FontWeight.Bold) 
                            } 
                        }
                    ) {
                        cart.items.forEachIndexed { index, item ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = "${item.foodName} × ${item.quantity}", 
                                        style = MaterialTheme.typography.titleSmall, 
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text("₹${fmt.format(item.itemTotal)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                                }
                                if (item.selectedCustomizations.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val customText = item.selectedCustomizations.joinToString(", ") { it.optionName }
                                    Text("• $customText", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (!item.specialInstructions.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Note: ${item.specialInstructions}", style = MaterialTheme.typography.labelSmall, color = GagOrange)
                                }
                            }
                            if (index < cart.items.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }

                // Pickup slot
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    CheckoutSection(title = "Pickup Slot") {
                        when (val slotsState = uiState.availableSlots) {
                            is UiState.Loading -> {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = GagPink)
                                }
                            }
                            is UiState.Empty -> {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = GagErrorContainer
                                ) {
                                    Text(
                                        "No pickup slots available for this outlet today.", 
                                        color = GagError, 
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            is UiState.Error -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Text("Couldn't load pickup slots. Try again.", color = GagError, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(onClick = { viewModel.loadPickupSlots(cart.outletId) }) {
                                        Text("Retry", color = GagPink, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            is UiState.Success -> {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    slotsState.data.forEach { slot ->
                                        val isSelected = uiState.selectedSlot?.id == slot.id
                                        val isFull = slot.status == com.srmfood.gag.domain.model.SlotStatus.FULL
                                        
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) GagPink.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
                                            border = androidx.compose.foundation.BorderStroke(2.dp, if (isSelected) GagPink else androidx.compose.ui.graphics.Color.Transparent),
                                            modifier = Modifier.clickable(enabled = !isFull) { viewModel.onSlotSelected(slot) }
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    slot.displayTime, 
                                                    style = MaterialTheme.typography.bodyLarge, 
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isFull) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (isFull) {
                                                    Text("FULL", style = MaterialTheme.typography.labelSmall, color = GagError, fontWeight = FontWeight.Bold)
                                                } else {
                                                    Text("${slot.availableCount} left", style = MaterialTheme.typography.labelSmall, color = GagSuccess, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }

                // Payment method
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    CheckoutSection(title = "Payment Method") {
                        PaymentMethod.values().filter { it == PaymentMethod.ONLINE }.forEach { method ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (uiState.selectedPaymentMethod == method) GagPink.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (uiState.selectedPaymentMethod == method) GagPink else androidx.compose.ui.graphics.Color.Transparent),
                                modifier = Modifier.fillMaxWidth().clickable { viewModel.onPaymentMethodSelected(method) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (uiState.selectedPaymentMethod == method) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (uiState.selectedPaymentMethod == method) GagPink else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("Razorpay", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("Secure online payment", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // Special instructions
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    CheckoutSection(title = "Special Instructions") {
                        OutlinedTextField(
                            value = uiState.specialInstructions,
                            onValueChange = viewModel::onInstructionsChanged,
                            placeholder = { Text("e.g. Less spice, extra sauce…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GagPink,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                cursorColor = GagPink
                            )
                        )
                    }
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
                                Text("₹${fmt.format(cart.subtotal)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("GST (5%)", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("₹${fmt.format(cart.tax)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                                Text("₹${fmt.format(cart.total)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = GagPink)
                            }
                        }
                    }
                }

                if (uiState.orderState is UiState.Error) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = GagErrorContainer
                        ) {
                            Text(
                                (uiState.orderState as UiState.Error).message, 
                                color = GagError, 
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                if (uiState.paymentVerificationState is UiState.Error) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = GagErrorContainer
                        ) {
                            Text(
                                (uiState.paymentVerificationState as UiState.Error).message, 
                                color = GagError, 
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckoutSection(
    title: String, 
    action: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp), 
        color = MaterialTheme.colorScheme.surface, 
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                if (action != null) {
                    action()
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
