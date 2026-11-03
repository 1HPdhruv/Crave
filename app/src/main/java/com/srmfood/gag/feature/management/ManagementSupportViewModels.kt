package com.srmfood.gag.feature.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.SupportTicketCategory
import com.srmfood.gag.domain.model.SupportTicketDetail
import com.srmfood.gag.domain.model.SupportTicketPriority
import com.srmfood.gag.domain.model.SupportTicketQueueFilter
import com.srmfood.gag.domain.model.SupportTicketStatus
import com.srmfood.gag.domain.model.SupportTicketSummary
import com.srmfood.gag.domain.repository.SupportTicketRepository
import com.srmfood.gag.feature.support.SupportLimits
import com.srmfood.gag.feature.support.requiresReasonWhenMovingTo
import com.srmfood.gag.feature.support.supportErrorMessage
import com.srmfood.gag.feature.support.validateReason
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.gotrue.Auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Queue ────────────────────────────────────────────────────────────────────

data class ManagementSupportQueueState(
    val tickets: List<SupportTicketSummary> = emptyList(),
    val totalCount: Long = 0,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val search: String = "",
    val statuses: Set<SupportTicketStatus> = emptySet(),
    val categories: Set<SupportTicketCategory> = emptySet(),
    val priorities: Set<SupportTicketPriority> = emptySet()
) {
    val canLoadMore get() = tickets.size < totalCount
    fun toFilter() = SupportTicketQueueFilter(
        statuses = statuses.takeIf { it.isNotEmpty() }?.toList(),
        categories = categories.takeIf { it.isNotEmpty() }?.toList(),
        priorities = priorities.takeIf { it.isNotEmpty() }?.toList(),
        search = search.trim().takeIf { it.isNotEmpty() }
    )
}

@HiltViewModel
class ManagementSupportViewModel @Inject constructor(
    private val repository: SupportTicketRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ManagementSupportQueueState())
    val state: StateFlow<ManagementSupportQueueState> = _state.asStateFlow()

    private var searchJob: Job? = null
    /** Bumped on every reload so a slow earlier response can't overwrite newer filters. */
    private var generation = 0

    init { reload() }

    fun reload() {
        val gen = ++generation
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getQueue(_state.value.toFilter(), PAGE_SIZE, 0).fold(
                onSuccess = { page ->
                    if (gen == generation) _state.update {
                        it.copy(tickets = page.tickets, totalCount = page.totalCount, isLoading = false)
                    }
                },
                onFailure = { e ->
                    if (gen == generation) _state.update { it.copy(isLoading = false, error = supportErrorMessage(e)) }
                }
            )
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.canLoadMore) return
        val gen = generation
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            repository.getQueue(s.toFilter(), PAGE_SIZE, s.tickets.size).fold(
                onSuccess = { page ->
                    if (gen == generation) _state.update {
                        it.copy(tickets = (it.tickets + page.tickets).distinctBy { t -> t.id },
                            totalCount = page.totalCount, isLoadingMore = false)
                    }
                },
                onFailure = { e -> _state.update { it.copy(isLoadingMore = false, error = supportErrorMessage(e)) } }
            )
        }
    }

    fun setSearch(q: String) {
        _state.update { it.copy(search = q.take(100)) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(400); reload() }
    }

    fun toggleStatus(s: SupportTicketStatus) { _state.update { it.copy(statuses = it.statuses.toggle(s)) }; reload() }
    fun toggleCategory(c: SupportTicketCategory) { _state.update { it.copy(categories = it.categories.toggle(c)) }; reload() }
    fun togglePriority(p: SupportTicketPriority) { _state.update { it.copy(priorities = it.priorities.toggle(p)) }; reload() }
    fun clearFilters() {
        _state.update { it.copy(search = "", statuses = emptySet(), categories = emptySet(), priorities = emptySet()) }
        reload()
    }

    private companion object { const val PAGE_SIZE = 50 }
}

internal fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

// ─── Detail ───────────────────────────────────────────────────────────────────

data class ManagementTicketUiState(
    val detail: UiState<SupportTicketDetail> = UiState.Loading,
    val responseDraft: String = "",
    val noteDraft: String = "",
    /** Non-null while the reason dialog is open for a transition. */
    val pendingTransition: SupportTicketStatus? = null,
    val reasonDraft: String = "",
    val reasonError: String? = null,
    val isBusy: Boolean = false,
    val actionError: String? = null,
    /** null = not available on the server (yet); the UI falls back to assign-to-me. */
    val assignees: List<com.srmfood.gag.domain.model.SupportAssignee>? = null,
    /** null = unavailable / not loaded; empty reviews object = none left. */
    val linkedReviews: com.srmfood.gag.domain.model.SupportTicketReviews? = null
)

@HiltViewModel
class ManagementTicketDetailViewModel @Inject constructor(
    private val repository: SupportTicketRepository,
    private val auth: Auth
) : ViewModel() {
    private val _state = MutableStateFlow(ManagementTicketUiState())
    val state: StateFlow<ManagementTicketUiState> = _state.asStateFlow()
    private var ticketId = ""

    val currentUserId: String? get() = auth.currentSessionOrNull()?.user?.id

    fun load(ticketId: String) {
        this.ticketId = ticketId
        _state.update { it.copy(detail = UiState.Loading) }
        refresh()
        loadExtras()
    }

    /** Optional context; failures (e.g. helper functions not deployed) just hide the feature. */
    private fun loadExtras() {
        viewModelScope.launch {
            repository.listAssignees().onSuccess { list -> _state.update { it.copy(assignees = list) } }
        }
        viewModelScope.launch {
            repository.getTicketReviews(ticketId).onSuccess { r -> _state.update { it.copy(linkedReviews = r) } }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            repository.getTicketDetail(ticketId).fold(
                onSuccess = { d -> _state.update { it.copy(detail = UiState.Success(d)) } },
                onFailure = { e ->
                    // Keep showing stale data if we already have some; only a first load is a hard error.
                    if (_state.value.detail !is UiState.Success) _state.update { s -> s.copy(detail = UiState.Error(supportErrorMessage(e))) }
                    else _state.update { s -> s.copy(actionError = supportErrorMessage(e)) }
                }
            )
        }
    }

    fun setResponseDraft(v: String) = _state.update { it.copy(responseDraft = v.take(SupportLimits.MESSAGE_MAX)) }
    fun setNoteDraft(v: String) = _state.update { it.copy(noteDraft = v.take(SupportLimits.NOTE_MAX)) }
    fun setReasonDraft(v: String) = _state.update { it.copy(reasonDraft = v.take(SupportLimits.REASON_MAX), reasonError = null) }
    fun dismissError() = _state.update { it.copy(actionError = null) }

    /** Runs one write at a time; refreshes from the server afterwards so UI always shows authoritative state. */
    private fun act(onSuccess: () -> Unit = {}, block: suspend () -> Result<*>) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, actionError = null) }
        viewModelScope.launch {
            block().fold(
                onSuccess = { onSuccess(); _state.update { it.copy(isBusy = false) }; refresh() },
                onFailure = { e ->
                    _state.update { it.copy(isBusy = false, actionError = supportErrorMessage(e)) }
                    refresh() // e.g. TICKET_CHANGED: show what the other manager did
                }
            )
        }
    }

    fun sendResponse() {
        val body = _state.value.responseDraft
        if (body.isBlank()) return
        act(onSuccess = { _state.update { it.copy(responseDraft = "") } }) { repository.respond(ticketId, body) }
    }

    fun addNote() {
        val note = _state.value.noteDraft
        if (note.isBlank()) return
        act(onSuccess = { _state.update { it.copy(noteDraft = "") } }) { repository.addInternalNote(ticketId, note) }
    }

    fun setPriority(p: SupportTicketPriority) = act { repository.setPriority(ticketId, p) }

    fun assignToMe() { currentUserId?.let { me -> act { repository.assign(ticketId, me) } } }
    fun assignTo(adminId: String) = act { repository.assign(ticketId, adminId) }
    fun unassign() = act { repository.assign(ticketId, null) }

    /** Starts a status change; opens the reason dialog when the server will demand one. */
    fun requestTransition(target: SupportTicketStatus) {
        val current = (_state.value.detail as? UiState.Success)?.data?.ticket?.status ?: return
        if (target !in current.allowedTransitions) return
        if (current.requiresReasonWhenMovingTo(target)) {
            _state.update { it.copy(pendingTransition = target, reasonDraft = "", reasonError = null) }
        } else {
            applyTransition(current, target, null)
        }
    }

    fun cancelTransition() = _state.update { it.copy(pendingTransition = null, reasonDraft = "", reasonError = null) }

    fun confirmTransition() {
        val s = _state.value
        val target = s.pendingTransition ?: return
        val current = (s.detail as? UiState.Success)?.data?.ticket?.status ?: return
        val err = validateReason(s.reasonDraft)
        if (err != null) { _state.update { it.copy(reasonError = err) }; return }
        applyTransition(current, target, s.reasonDraft)
    }

    private fun applyTransition(from: SupportTicketStatus, to: SupportTicketStatus, reason: String?) {
        if (_state.value.isBusy) return
        // expectedStatus makes the server reject the change if another manager moved the ticket first.
        act(onSuccess = { cancelTransition() }) { repository.setStatus(ticketId, to, reason, from) }
    }
}
