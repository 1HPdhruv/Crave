package com.srmfood.gag.feature.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.SupportTicket
import com.srmfood.gag.domain.model.SupportTicketCategory
import com.srmfood.gag.domain.model.SupportTicketMessage
import com.srmfood.gag.domain.repository.SupportTicketRepository
import com.srmfood.gag.domain.usecase.order.GetOrderDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Report a problem ─────────────────────────────────────────────────────────

data class ReportProblemUiState(
    val order: UiState<Order> = UiState.Loading,
    val category: SupportTicketCategory = SupportTicketCategory.ORDER,
    val foodItemId: String? = null,
    val subject: String = "",
    val description: String = "",
    val showValidation: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    /** Set once the server confirmed the ticket (new or the original on retry). */
    val createdTicket: SupportTicket? = null,
    /** Existing active ticket the server pointed us at (TICKET_ALREADY_OPEN). */
    val existingTicketId: String? = null
) {
    val errors: TicketFormErrors get() = validateTicketForm(subject, description)
}

@HiltViewModel
class ReportProblemViewModel @Inject constructor(
    private val getOrderDetails: GetOrderDetailsUseCase,
    private val repository: SupportTicketRepository,
    private val requestIds: SupportRequestIdStore
) : ViewModel() {

    private val _state = MutableStateFlow(ReportProblemUiState())
    val state: StateFlow<ReportProblemUiState> = _state.asStateFlow()

    private var orderId: String = ""

    fun load(orderId: String) {
        this.orderId = orderId
        viewModelScope.launch {
            _state.update { it.copy(order = UiState.Loading) }
            getOrderDetails(orderId).fold(
                onSuccess = { o -> _state.update { it.copy(order = UiState.Success(o)) } },
                onFailure = { e -> _state.update { it.copy(order = UiState.Error(supportErrorMessage(e))) } }
            )
        }
    }

    fun setCategory(c: SupportTicketCategory) = _state.update {
        // Item picking is only meaningful for item-specific categories.
        it.copy(category = c, foodItemId = if (c == SupportTicketCategory.FOOD_QUALITY || c == SupportTicketCategory.MISSING_ITEMS) it.foodItemId else null)
    }
    fun setFoodItem(id: String?) = _state.update { it.copy(foodItemId = id) }
    fun setSubject(v: String) = _state.update { it.copy(subject = v.take(SupportLimits.SUBJECT_MAX + 20), submitError = null) }
    fun setDescription(v: String) = _state.update { it.copy(description = v.take(SupportLimits.DESCRIPTION_MAX + 50), submitError = null) }

    private fun requestKey(category: SupportTicketCategory) = "$orderId|${category.name}"

    fun submit() {
        val snapshot = _state.value
        // Synchronous guard: repeated taps cannot start a second request.
        if (snapshot.isSubmitting || snapshot.createdTicket != null || snapshot.order !is UiState.Success) return
        if (snapshot.errors.hasErrors) {
            _state.update { it.copy(showValidation = true) }
            return
        }
        val category = snapshot.category
        val key = requestKey(category)
        _state.update { it.copy(isSubmitting = true, submitError = null, showValidation = true) }
        viewModelScope.launch {
            // Same key until success/definitive failure → retries and restarts are idempotent.
            val requestId = requestIds.getOrCreate(key)
            repository.createTicket(
                category = category,
                subject = snapshot.subject,
                description = snapshot.description,
                orderId = orderId,
                foodItemId = snapshot.foodItemId,
                outletId = null, // server derives the outlet from the order
                clientRequestId = requestId
            ).fold(
                onSuccess = { ticket ->
                    requestIds.clear(key)
                    _state.update { it.copy(isSubmitting = false, createdTicket = ticket) }
                },
                onFailure = { e ->
                    if (isDefinitiveCreateFailure(e)) requestIds.clear(key)
                    val existing = Regex("TICKET_ALREADY_OPEN:([0-9a-fA-F-]{36})").find(e.message.orEmpty())?.groupValues?.get(1)
                    _state.update { it.copy(isSubmitting = false, submitError = supportErrorMessage(e), existingTicketId = existing) }
                }
            )
        }
    }
}

// ─── My tickets ───────────────────────────────────────────────────────────────

@HiltViewModel
class MyTicketsViewModel @Inject constructor(
    private val repository: SupportTicketRepository
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<SupportTicket>>>(UiState.Loading)
    val state: StateFlow<UiState<List<SupportTicket>>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.getMyTickets().fold(
                onSuccess = { _state.value = if (it.isEmpty()) UiState.Empty else UiState.Success(it) },
                onFailure = { _state.value = UiState.Error(supportErrorMessage(it)) }
            )
        }
    }
}

// ─── Ticket detail (student) ──────────────────────────────────────────────────

data class StudentTicketUiState(
    val ticket: UiState<SupportTicket> = UiState.Loading,
    val messages: List<SupportTicketMessage> = emptyList(),
    val reply: String = "",
    val isSending: Boolean = false,
    val sendError: String? = null
) {
    val canReply: Boolean
        get() = (ticket as? UiState.Success)?.data?.status?.isActive == true
}

@HiltViewModel
class StudentTicketViewModel @Inject constructor(
    private val repository: SupportTicketRepository
) : ViewModel() {
    private val _state = MutableStateFlow(StudentTicketUiState())
    val state: StateFlow<StudentTicketUiState> = _state.asStateFlow()
    private var ticketId = ""

    fun load(ticketId: String) {
        this.ticketId = ticketId
        viewModelScope.launch {
            _state.update { it.copy(ticket = UiState.Loading) }
            val t = repository.getMyTicket(ticketId)
            val m = repository.getMessages(ticketId)
            t.fold(
                onSuccess = { ticket ->
                    _state.update { it.copy(ticket = UiState.Success(ticket), messages = m.getOrDefault(it.messages)) }
                },
                onFailure = { e -> _state.update { it.copy(ticket = UiState.Error(supportErrorMessage(e))) } }
            )
        }
    }

    fun setReply(v: String) = _state.update { it.copy(reply = v.take(SupportLimits.MESSAGE_MAX), sendError = null) }

    fun send() {
        val s = _state.value
        if (s.isSending || !s.canReply || s.reply.isBlank()) return
        _state.update { it.copy(isSending = true, sendError = null) }
        viewModelScope.launch {
            repository.addStudentMessage(ticketId, s.reply).fold(
                onSuccess = { msg ->
                    _state.update { it.copy(isSending = false, reply = "", messages = it.messages + msg) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isSending = false, sendError = supportErrorMessage(e)) }
                    // Status may have changed under us (e.g. resolved): refresh.
                    if ("TICKET_CLOSED" in e.message.orEmpty()) load(ticketId)
                }
            )
        }
    }
}
