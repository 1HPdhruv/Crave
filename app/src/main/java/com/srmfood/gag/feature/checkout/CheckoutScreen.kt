package com.srmfood.gag.feature.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        topBar = { GagTopBar(title = "Checkout", onBack = onBack) },
        containerColor = GagBackground,
        bottomBar = {
            Surface(color = GagBackground, shadowElevation = 8.dp) {
                GagPrimaryButton(
                    text = if (uiState.selectedSlot == null) "Select a Pickup Slot First" else "Place Order",
                    onClick = viewModel::placeOrder,
                    enabled = uiState.selectedSlot != null && uiState.cart != null,
                    isLoading = uiState.orderState is UiState.Loading,
                    modifier = Modifier.padding(16.dp).navigationBarsPadding()
                )
            }
        }
    ) { padding ->
        val cart = uiState.cart
        if (cart == null) {
            GagLoadingScreen(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Order summary
                item {
                    CheckoutSection(title = "Order Summary") {
                        cart.items.forEach { item ->
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${item.foodName} × ${item.quantity}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text("₹${item.itemTotal.toInt()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                if (item.selectedCustomizations.isNotEmpty()) {
                                    val customText = item.selectedCustomizations.joinToString(", ") { it.optionName }
                                    Text(customText, style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                                }
                                if (!item.specialInstructions.isNullOrBlank()) {
                                    Text("Note: ${item.specialInstructions}", style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = GagOutlineVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)
                            Text("₹${cart.subtotal.toInt()}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("GST (5%)", style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)
                            Text("₹${cart.tax.toInt()}", style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = GagOutlineVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("₹${cart.total.toInt()}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GagOrange)
                        }
                    }
                }

                // Pickup slot
                item {
                    CheckoutSection(title = "Pickup Slot") {
                        when (val slotsState = uiState.availableSlots) {
                            is UiState.Loading -> {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = GagOrange)
                                }
                            }
                            is UiState.Empty -> {
                                Text("No pickup slots available for this outlet today.", color = GagOnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            is UiState.Error -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Text("Couldn't load pickup slots. Try again.", color = GagError, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(onClick = { viewModel.loadPickupSlots(cart.outletId) }) {
                                        Text("Retry", color = GagOrange)
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
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) GagOrange.copy(alpha = 0.15f) else GagSurface,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) GagOrange else GagOutlineVariant),
                                            modifier = Modifier.clickable(enabled = !isFull) { viewModel.onSlotSelected(slot) }
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    slot.displayTime, 
                                                    style = MaterialTheme.typography.bodyMedium, 
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isFull) GagOnSurfaceVariant.copy(alpha = 0.5f) else GagOnBackground
                                                )
                                                if (isFull) {
                                                    Text("FULL", style = MaterialTheme.typography.labelSmall, color = GagError)
                                                } else {
                                                    Text("${slot.availableCount} left", style = MaterialTheme.typography.labelSmall, color = GagOnSurfaceVariant)
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
                    CheckoutSection(title = "Payment Method") {
                        PaymentMethod.values().filter { it == PaymentMethod.ONLINE }.forEach { method ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { viewModel.onPaymentMethodSelected(method) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (uiState.selectedPaymentMethod == method) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (uiState.selectedPaymentMethod == method) GagOrange else GagOnSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(method.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                // Special instructions
                item {
                    CheckoutSection(title = "Special Instructions (Optional)") {
                        OutlinedTextField(
                            value = uiState.specialInstructions,
                            onValueChange = viewModel::onInstructionsChanged,
                            placeholder = { Text("e.g. Less spice, extra sauce…", color = GagOnSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GagOrange,
                                unfocusedBorderColor = GagOutline,
                                cursorColor = GagOrange
                            )
                        )
                    }
                }

                if (uiState.orderState is UiState.Error) {
                    item {
                        Text((uiState.orderState as UiState.Error).message, color = GagError, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (uiState.paymentVerificationState is UiState.Error) {
                    item {
                        Text((uiState.paymentVerificationState as UiState.Error).message, color = GagError, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckoutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = GagSurface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
