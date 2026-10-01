package com.srmfood.gag.feature.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagPrimaryButton
import com.srmfood.gag.core.ui.component.HostelAddressBanner
import com.srmfood.gag.core.ui.component.HostelAddressDialog
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.Cart
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.PaymentMethod
import com.srmfood.gag.domain.model.PaymentVerificationRequest
import com.srmfood.gag.domain.model.PickupSlot
import com.srmfood.gag.domain.model.RazorpayOrderDetails
import com.srmfood.gag.core.payment.RazorpayManager
import com.srmfood.gag.core.payment.RazorpayResult
import com.srmfood.gag.domain.repository.HostelAddress
import com.srmfood.gag.domain.repository.OrderingMode
import com.srmfood.gag.domain.repository.OrderingModeRepository
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.razorpay.Checkout
import org.json.JSONObject
import android.app.Activity
import androidx.compose.ui.platform.LocalContext

// ─── UiState ────────────────────────────────────────────────────────────────────────────────────

data class CheckoutUiState(
    val cart: Cart? = null,
    val selectedPickupDate: java.time.LocalDate = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate(),
    val availablePickupDates: List<java.time.LocalDate> = (0..3).map {
        java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate().plusDays(it.toLong())
    },
    val availableSlots: UiState<List<PickupSlot>> = UiState.Loading,
    val selectedSlot: PickupSlot? = null,
    val selectedPaymentMethod: PaymentMethod = PaymentMethod.ONLINE,
    val specialInstructions: String = "",
    val orderState: UiState<Order> = UiState.Idle,
    val razorpayOrderDetails: RazorpayOrderDetails? = null,
    val paymentVerificationState: UiState<Unit> = UiState.Idle,
    val orderingMode: OrderingMode = OrderingMode.PICKUP,
    val hostelAddress: HostelAddress = HostelAddress()
)

sealed class CheckoutUiEvent {
    data class OpenRazorpay(val details: RazorpayOrderDetails, val orderId: String) : CheckoutUiEvent()
    data class OrderSuccess(val orderId: String) : CheckoutUiEvent()
    data class ShowError(val message: String) : CheckoutUiEvent()
}

// ─── Default slot selection (IST, ~15-min lead time) ─────────────────────────────────────────

/**
 * Selects the best default slot for [requestedDate]:
 *  - For today: earliest slot whose startTime >= nowIST + leadMinutes, excluding FULL slots.
 *  - For future dates: first non-FULL slot (list is already sorted by Supabase).
 */
private fun selectDefaultSlot(
    slots: List<PickupSlot>,
    requestedDate: java.time.LocalDate,
    nowIST: java.time.LocalTime,
    todayIST: java.time.LocalDate,
    leadMinutes: Long = 15L
): PickupSlot? {
    val isToday = requestedDate == todayIST
    val cutoff = if (isToday) nowIST.plusMinutes(leadMinutes) else java.time.LocalTime.MIN

    return slots
        .filter { slot -> slot.status != com.srmfood.gag.domain.model.SlotStatus.FULL }
        .filter { slot ->
            if (!isToday) return@filter true
            try {
                val sp = slot.startTime.substringBefore("+").substringBefore("Z").trim().split(":")
                val slotStart = java.time.LocalTime.of(sp[0].toInt(), sp.getOrNull(1)?.toInt() ?: 0)
                !slotStart.isBefore(cutoff)
            } catch (e: Exception) { false }
        }
        .minByOrNull { slot ->
            try {
                val sp = slot.startTime.substringBefore("+").substringBefore("Z").trim().split(":")
                java.time.LocalTime.of(sp[0].toInt(), sp.getOrNull(1)?.toInt() ?: 0)
            } catch (e: Exception) { java.time.LocalTime.MAX }
        }
}

// ─── ViewModel ────────────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val getCartUseCase: GetCartUseCase,
    private val placeOrderUseCase: PlaceOrderUseCase,
    private val cancelOrderUseCase: com.srmfood.gag.domain.usecase.order.CancelOrderUseCase,
    private val getPickupSlotsUseCase: com.srmfood.gag.domain.usecase.order.GetPickupSlotsUseCase,
    private val cartRepository: com.srmfood.gag.domain.repository.CartRepository,
    private val paymentRepository: PaymentRepository,
    private val razorpayManager: RazorpayManager,
    private val orderingModeRepository: OrderingModeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CheckoutUiEvent>()
    val events: SharedFlow<CheckoutUiEvent> = _events.asSharedFlow()

    private var currentOrderId: String? = null
    private var hasLoadedSlots = false
    private var loadSlotsJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            getCartUseCase().collectLatest { cart ->
                _uiState.value = _uiState.value.copy(cart = cart)
                if (cart != null && !hasLoadedSlots) {
                    hasLoadedSlots = true
                    loadPickupSlots(cart.outletId, _uiState.value.selectedPickupDate, autoSelectDefault = true)
                }
            }
        }

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
                        android.util.Log.e("Razorpay", "Payment Error: code=${result.code}, message=${result.message}")
                        
                        var isCancelled = false
                        if (result.code == Checkout.PAYMENT_CANCELED) {
                            isCancelled = true
                        } else {
                            val msg = result.message ?: ""
                            if (msg.contains("\"reason\":\"payment_cancelled\"") || 
                                (msg.contains("\"source\":\"customer\"") && msg.contains("\"step\":\"payment_authentication\""))) {
                                isCancelled = true
                            }
                        }

                        if (isCancelled) {
                            _uiState.value = _uiState.value.copy(
                                orderState = UiState.Error("Payment cancelled\nYour payment was cancelled. No amount was charged.")
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                orderState = UiState.Error("Payment failed\nWe couldn't complete your payment. Please try again.")
                            )
                        }
                    }
                }
            }
        }
    }

    fun saveHostelAddress(address: HostelAddress) {
        viewModelScope.launch { orderingModeRepository.setHostelAddress(address) }
    }

    fun onPickupDateSelected(date: java.time.LocalDate) {
        // Immediately clear selectedSlot before loading new slots to prevent stale slot from surviving.
        _uiState.value = _uiState.value.copy(
            selectedPickupDate = date,
            selectedSlot = null
        )
        val cart = _uiState.value.cart
        if (cart != null) {
            loadPickupSlots(cart.outletId, date, autoSelectDefault = true)
        }
    }

    fun loadPickupSlots(
        outletId: String,
        requestedDate: java.time.LocalDate,
        autoSelectDefault: Boolean = false
    ) {
        loadSlotsJob?.cancel()
        loadSlotsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(availableSlots = UiState.Loading)

            val ist = java.time.ZoneId.of("Asia/Kolkata")
            val nowDateTime = java.time.ZonedDateTime.now(ist)
            val todayIST: java.time.LocalDate = nowDateTime.toLocalDate()
            val nowIST: java.time.LocalTime = nowDateTime.toLocalTime()
            val queryString = requestedDate.toString()

            android.util.Log.d("SlotFilter", "=== loadPickupSlots ===")
            android.util.Log.d("SlotFilter", "timezone=Asia/Kolkata requestedDate=$requestedDate today=$todayIST currentTime=${nowIST.withNano(0)}")

            val result = getPickupSlotsUseCase(outletId, queryString)

            // First stale check — discard response if date changed while awaiting RPC
            if (requestedDate != _uiState.value.selectedPickupDate) {
                android.util.Log.d("SlotFilter", "Discarding stale slot response for $requestedDate")
                return@launch
            }

            val newSlotsState = result.fold(
                onSuccess = { slots ->
                    android.util.Log.d("SlotFilter", "received=${slots.size} slots from Supabase")

                    val validSlots = slots.filter { slot ->
                        try {
                            val dp = slot.date.take(10).split("-")
                            val slotDate = java.time.LocalDate.of(dp[0].toInt(), dp[1].toInt(), dp[2].toInt())
                            val sp = slot.startTime.substringBefore("+").substringBefore("Z").trim().split(":")
                            val slotStart = java.time.LocalTime.of(sp[0].toInt(), sp.getOrNull(1)?.toInt() ?: 0)

                            val keep = if (slotDate == todayIST) {
                                slotStart.isAfter(nowIST)
                            } else {
                                true
                            }

                            if (!keep) {
                                android.util.Log.d("SlotFilter", "EXCLUDED date=$slotDate start=$slotStart (now=${nowIST.withNano(0)})")
                            } else {
                                android.util.Log.d("SlotFilter", "INCLUDED date=$slotDate start=$slotStart")
                            }
                            keep
                        } catch (e: Exception) {
                            android.util.Log.e("SlotFilter", "Parse error for slot date=\'${slot.date}\' startTime=\'${slot.startTime}\'", e)
                            false
                        }
                    }

                    android.util.Log.d("SlotFilter", "filtered=${validSlots.size} slots after applying IST filter")
                    if (validSlots.isEmpty()) UiState.Empty else UiState.Success(validSlots)
                },
                onFailure = { e ->
                    android.util.Log.e("SlotFilter", "Failed to fetch slots: ${e.message}", e)
                    UiState.Error(e.message ?: "Failed to load slots")
                }
            )

            // Second stale check — discard if date changed while processing results
            if (requestedDate != _uiState.value.selectedPickupDate) {
                android.util.Log.d("SlotFilter", "Discarding stale slot state for $requestedDate (second check)")
                return@launch
            }

            // Auto-select default slot when no slot is currently selected
            val autoSelected: PickupSlot? = if (autoSelectDefault && _uiState.value.selectedSlot == null) {
                when (newSlotsState) {
                    is UiState.Success -> {
                        val nowDt = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
                        selectDefaultSlot(
                            slots = newSlotsState.data,
                            requestedDate = requestedDate,
                            nowIST = nowDt.toLocalTime(),
                            todayIST = nowDt.toLocalDate(),
                            leadMinutes = 15L
                        )
                    }
                    else -> null
                }
            } else null

            _uiState.value = _uiState.value.copy(
                availableSlots = newSlotsState,
                selectedSlot = autoSelected ?: _uiState.value.selectedSlot
            )

            if (autoSelected != null) {
                android.util.Log.d("SlotFilter", "Auto-selected default slot: ${autoSelected.id} @ ${autoSelected.startTime}")
            }
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

        // Defensive check: slot date must match selected date — never send stale slot
        if (slot.date.take(10) != state.selectedPickupDate.toString()) {
            _uiState.value = _uiState.value.copy(
                selectedSlot = null,
                orderState = UiState.Error("Please select a pickup slot for the selected date.")
            )
            return
        }

        if (state.orderingMode == OrderingMode.DELIVERY && !state.hostelAddress.isComplete) {
            _uiState.value = _uiState.value.copy(
                orderState = UiState.Error("Please add your hostel delivery address before placing the order.")
            )
            return
        }

        val combinedInstructions = when {
            state.orderingMode == OrderingMode.DELIVERY -> {
                val base = state.hostelAddress.toDeliveryInstructions()
                if (state.specialInstructions.isNotBlank()) "$base | ${state.specialInstructions}" else base
            }
            state.specialInstructions.isNotBlank() -> state.specialInstructions
            else -> null
        }

        viewModelScope.launch {
            if (currentOrderId != null) {
                cancelOrderUseCase(currentOrderId!!, "Payment retried or cancelled by user")
                currentOrderId = null
            }

            _uiState.value = _uiState.value.copy(orderState = UiState.Loading)

            val syncResult = cartRepository.syncCart()
            val remoteCartId = syncResult.getOrElse { e ->
                android.util.Log.e("PlaceOrderDiag", "syncCart failed", e)
                _uiState.value = _uiState.value.copy(
                    orderState = UiState.Error("Cart sync failed: ${e.message}")
                )
                return@launch
            }

            android.util.Log.d("PlaceOrderDiag", "=== CHECKOUT VIEWMODEL placeOrder ===")
            android.util.Log.d("PlaceOrderDiag", "Checkout Cart Item Count: ${cart.items.size}")
            android.util.Log.d("PlaceOrderDiag", "Checkout Cart Outlet ID: ${cart.outletId}")
            android.util.Log.d("PlaceOrderDiag", "Checkout Selected Slot ID: ${slot.id}")
            android.util.Log.d("PlaceOrderDiag", "Resolved Remote Cart ID: $remoteCartId")
            android.util.Log.d("PlaceOrderDiag", "=======================================")

            val result = placeOrderUseCase(
                cartId = remoteCartId,
                outletId = cart.outletId,
                pickupSlotId = slot.id,
                paymentMethod = state.selectedPaymentMethod,
                specialInstructions = combinedInstructions
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
                val errorMsg = error.message ?: "Failed"
                val displayMsg = if (errorMsg.contains("Outlet is temporarily closed right now")) {
                    "Outlet is temporarily closed right now.\nPlease select a pickup slot for tomorrow or a later date."
                } else {
                    errorMsg
                }
                _uiState.value = _uiState.value.copy(orderState = UiState.Error(displayMsg))
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
                    cartRepository.clearCart()
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

// ─── Screen ────────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    onBack: () -> Unit,
    onOrderPlaced: (String) -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val fmt = NumberFormat.getInstance(Locale("en", "IN"))
    var showAddressDialog by remember { mutableStateOf(false) }
    var showPickupSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CheckoutUiEvent.OrderSuccess -> onOrderPlaced(event.orderId)
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
                                put("color", "#F44336")
                            })
                        }
                        co.open(activity, options)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                is CheckoutUiEvent.ShowError -> { /* handled via state */ }
            }
        }
    }

    if (showPickupSheet) {
        PickupScheduleBottomSheet(
            uiState = uiState,
            onDismiss = { showPickupSheet = false },
            onDateSelected = { date -> viewModel.onPickupDateSelected(date) },
            onSlotSelected = { slot ->
                viewModel.onSlotSelected(slot)
                showPickupSheet = false
            },
            onRetrySlots = {
                val cart = uiState.cart
                if (cart != null) viewModel.loadPickupSlots(cart.outletId, uiState.selectedPickupDate)
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (uiState.cart != null) {
                val isDelivery = uiState.orderingMode == OrderingMode.DELIVERY
                val isReadyForPayment = uiState.selectedSlot != null &&
                    uiState.availableSlots !is UiState.Loading &&
                    (!isDelivery || uiState.hostelAddress.isComplete)

                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 24.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = GagSpacing.Large, vertical = 20.dp)
                            .navigationBarsPadding()
                    ) {
                        GagPrimaryButton(
                            text = when {
                                uiState.availableSlots is UiState.Loading -> "Loading slots…"
                                uiState.selectedSlot == null -> "Select Pickup Time"
                                isDelivery && !uiState.hostelAddress.isComplete -> "Add Hostel Address"
                                else -> "Pay ₹${fmt.format(uiState.cart!!.total)}"
                            },
                            onClick = viewModel::placeOrder,
                            enabled = isReadyForPayment,
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
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 40.dp
                )
            ) {
                // 1. Header
                item {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = GagSpacing.Large, vertical = GagSpacing.Large)
                            .statusBarsPadding()
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Go back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "CHECKOUT", 
                            style = MaterialTheme.typography.displaySmall, 
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    }
                }

                // 2. Order summary
                item {
                    CheckoutSectionTitle("YOUR ORDER")
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = GagSpacing.Large),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            cart.items.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${item.quantity} × ${item.foodName}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Black
                                        )
                                        if (item.selectedCustomizations.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val customText = item.selectedCustomizations.joinToString(", ") { it.optionName }
                                            Text("+ $customText", style = MaterialTheme.typography.labelMedium, color = GagPink, fontWeight = FontWeight.Bold)
                                        }
                                        if (!item.specialInstructions.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Note: ${item.specialInstructions}", style = MaterialTheme.typography.labelSmall, color = GagOrange, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text("₹${fmt.format(item.itemTotal)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                if (index < cart.items.size - 1) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(GagSpacing.ExtraLarge))
                }

                // 3. Pickup location & 4/5. Date/Slot
                item {
                    val isDelivery = uiState.orderingMode == OrderingMode.DELIVERY
                    CheckoutSectionTitle(if (isDelivery) "DELIVERY DETAILS" else "PICKUP DETAILS")
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = GagSpacing.Large),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            // Location part
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(GagPinkContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Storefront, contentDescription = null, tint = GagPink, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = if (isDelivery) "Deliver to hostel" else "Pickup from",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = cart.outletName.uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Time part (Trigger for BottomSheet)
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { showPickupSheet = true },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(GagPinkContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = GagPink, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = "When",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                        when {
                                            uiState.availableSlots is UiState.Loading -> {
                                                Text("Loading slots...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                            }
                                            uiState.selectedSlot != null -> {
                                                val ist = java.time.ZoneId.of("Asia/Kolkata")
                                                val todayIST = java.time.ZonedDateTime.now(ist).toLocalDate()
                                                val tomorrowIST = todayIST.plusDays(1)
                                                val dateLabel = when (uiState.selectedPickupDate) {
                                                    todayIST -> "Today"
                                                    tomorrowIST -> "Tomorrow"
                                                    else -> uiState.selectedPickupDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
                                                }
                                                Text("$dateLabel · ${uiState.selectedSlot!!.displayTime}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                            }
                                            else -> {
                                                Text("Select pickup time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = GagPink)
                                            }
                                        }
                                    }
                                }
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(GagSpacing.ExtraLarge))
                }
                
                // Special Instructions (Optional)
                item {
                    CheckoutSectionTitle("SPECIAL INSTRUCTIONS")
                    OutlinedTextField(
                        value = uiState.specialInstructions,
                        onValueChange = viewModel::onInstructionsChanged,
                        placeholder = { Text("e.g. Less spice, extra sauce…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = GagSpacing.Large),
                        maxLines = 3,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GagPink,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            cursorColor = GagPink,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(GagSpacing.ExtraLarge))
                }

                // 6. Payment
                item {
                    CheckoutSectionTitle("PAYMENT")
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = GagSpacing.Large),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(2.dp, GagPink) // Always Razorpay for now, so highlighted
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GagPink, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Razorpay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                Text("Secure online payment", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(GagSpacing.ExtraLarge))
                }

                // 7. Total
                item {
                    CheckoutSectionTitle("BILL DETAILS")
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = GagSpacing.Large),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Item total", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                Text("₹${fmt.format(cart.subtotal)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Tax", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                Text("₹${fmt.format(cart.tax)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                                Text("₹${fmt.format(cart.total)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = GagPink)
                            }
                        }
                    }
                }

                // Error State Display
                if (uiState.orderState is UiState.Error) {
                    item {
                        Spacer(modifier = Modifier.height(GagSpacing.ExtraLarge))
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = GagSpacing.Large),
                            shape = RoundedCornerShape(16.dp),
                            color = GagErrorContainer
                        ) {
                            Text(
                                (uiState.orderState as UiState.Error).message,
                                color = GagError,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                if (uiState.paymentVerificationState is UiState.Error) {
                    item {
                        Spacer(modifier = Modifier.height(GagSpacing.ExtraLarge))
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = GagSpacing.Large),
                            shape = RoundedCornerShape(16.dp),
                            color = GagErrorContainer
                        ) {
                            Text(
                                (uiState.paymentVerificationState as UiState.Error).message,
                                color = GagError,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
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
private fun CheckoutSectionTitle(title: String) {
    Text(
        text = title, 
        style = MaterialTheme.typography.labelLarge, 
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = GagSpacing.Large, end = GagSpacing.Large, bottom = GagSpacing.Medium)
    )
}

// ─── Pickup Schedule Bottom Sheet ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickupScheduleBottomSheet(
    uiState: CheckoutUiState,
    onDismiss: () -> Unit,
    onDateSelected: (java.time.LocalDate) -> Unit,
    onSlotSelected: (PickupSlot) -> Unit,
    onRetrySlots: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val ist = java.time.ZoneId.of("Asia/Kolkata")
    val todayIST = java.time.ZonedDateTime.now(ist).toLocalDate()
    val tomorrowIST = todayIST.plusDays(1)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = GagSpacing.Large)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "WHEN DO YOU WANT IT?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // ─ Date chips
            Text(
                text = "DATE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = GagPink,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(uiState.availablePickupDates) { date ->
                    val isSelected = uiState.selectedPickupDate == date
                    val label = when (date) {
                        todayIST -> "Today"
                        tomorrowIST -> "Tomorrow"
                        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d"))
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) GagPink else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                        modifier = Modifier.clickable { onDateSelected(date) }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ─ Slots
            Text(
                text = "AVAILABLE SLOTS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = GagPink,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (val slotsState = uiState.availableSlots) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GagPink, strokeWidth = 4.dp)
                    }
                }
                is UiState.Empty -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = GagErrorContainer
                    ) {
                        val isToday = uiState.selectedPickupDate == todayIST
                        Text(
                            text = if (isToday) {
                                "No more pickup slots available today.\nTry selecting another date."
                            } else {
                                "No pickup slots available for this date."
                            },
                            color = GagError,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(20.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is UiState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                    ) {
                        Text(
                            "Couldn\'t load pickup slots.",
                            color = GagError,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = onRetrySlots) {
                            Text("Try Again", color = GagPink, fontWeight = FontWeight.Black)
                        }
                    }
                }
                is UiState.Success -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        slotsState.data.chunked(2).forEach { rowSlots ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (slot in rowSlots) {
                                    val isSelected = uiState.selectedSlot?.id == slot.id
                                    val isFull = slot.status == com.srmfood.gag.domain.model.SlotStatus.FULL

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = when {
                                            isSelected -> OrbitLime.copy(alpha = 0.12f)
                                            isFull -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = when {
                                                isSelected -> GagPink
                                                isFull -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                else -> MaterialTheme.colorScheme.outlineVariant
                                            }
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable(enabled = !isFull) { onSlotSelected(slot) }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                slot.displayTime,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Black,
                                                color = when {
                                                    isFull -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                    isSelected -> GagPink
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            if (isFull) {
                                                Text(
                                                    "FULL",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = GagError.copy(alpha = 0.6f),
                                                    fontWeight = FontWeight.Black
                                                )
                                            } else {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(
                                                                when (slot.status) {
                                                                    com.srmfood.gag.domain.model.SlotStatus.LIMITED -> GagAccent
                                                                    else -> GagSuccess
                                                                }
                                                            )
                                                    )
                                                    Text(
                                                        "${slot.availableCount} left",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = when (slot.status) {
                                                            com.srmfood.gag.domain.model.SlotStatus.LIMITED -> GagAccent
                                                            else -> GagSuccess
                                                        },
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (rowSlots.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}
