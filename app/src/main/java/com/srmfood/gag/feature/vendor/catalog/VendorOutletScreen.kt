package com.srmfood.gag.feature.vendor.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.GagError
import com.srmfood.gag.domain.model.OutletOrderingStatus
import com.srmfood.gag.domain.model.vendor.*
import com.srmfood.gag.domain.repository.VendorCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

val ALL_DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

data class SlotDialog(val capacityText: String = "", val startText: String = "", val endText: String = "", val editing: VendorSlot? = null, val error: String? = null)

data class VendorOutletState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val outlets: List<VendorOutlet> = emptyList(),
    val selectedId: String? = null,
    // editable outlet form
    val isOpen: Boolean = false, val description: String = "", val phone: String = "",
    val openTime: String = "08:00", val closeTime: String = "22:00", val days: List<String> = emptyList(),
    val saving: Boolean = false,
    // slots
    val date: LocalDate = VendorRules.todayIst(),
    val slots: List<VendorSlot> = emptyList(),
    val slotsLoading: Boolean = false,
    val slotsError: String? = null,
    val dialog: SlotDialog? = null,
    val deleting: VendorSlot? = null,
    val slotBusy: Boolean = false,
    val message: String? = null
) {
    val outlet get() = outlets.firstOrNull { it.id == selectedId }
    val hoursError get() = VendorRules.validateHours(openTime, closeTime)
    val phoneError get() = VendorRules.validatePhone(phone)
}

@HiltViewModel
class VendorOutletViewModel @Inject constructor(private val repo: VendorCatalogRepository) : ViewModel() {
    private val _state = MutableStateFlow(VendorOutletState())
    val state: StateFlow<VendorOutletState> = _state.asStateFlow()
    private var slotsGen = 0

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            repo.getMyOutlets().fold(
                onSuccess = { list ->
                    if (list.isEmpty()) { _state.update { it.copy(loading = false, loadError = "No outlet is linked to your account yet.") }; return@fold }
                    val keep = _state.value.selectedId?.takeIf { id -> list.any { it.id == id } } ?: list.first().id
                    _state.update { it.copy(loading = false, outlets = list) }
                    select(keep)
                },
                onFailure = { e -> _state.update { it.copy(loading = false, loadError = vendorErrorMessage(e)) } }
            )
        }
    }

    fun select(id: String) {
        val o = _state.value.outlets.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(selectedId = id, isOpen = o.isOpen, description = o.description, phone = o.phone, openTime = o.openTime, closeTime = o.closeTime, days = o.daysOpen) }
        loadSlots()
    }

    fun edit(f: (VendorOutletState) -> VendorOutletState) = _state.update { f(it).copy(message = null) }
    fun toggleDay(d: String) = _state.update { it.copy(days = if (d in it.days) it.days - d else ALL_DAYS.filter { x -> x in it.days || x == d }) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun saveOutlet() {
        val s = _state.value; val o = s.outlet ?: return
        if (s.saving) return
        if (s.hoursError != null || s.phoneError != null) { _state.update { it.copy(message = s.hoursError ?: s.phoneError) }; return }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repo.updateOutlet(o.id, s.isOpen, s.description, s.phone, s.openTime, s.closeTime, s.days).fold(
                onSuccess = { _state.update { it.copy(saving = false, message = "Outlet saved") }; reloadOutletsOnly() },
                onFailure = { e -> _state.update { it.copy(saving = false, message = vendorErrorMessage(e)) } }
            )
        }
    }

    private fun reloadOutletsOnly() { viewModelScope.launch { repo.getMyOutlets().onSuccess { l -> _state.update { it.copy(outlets = l) } } } }

    // ── slots ──
    fun setDate(d: LocalDate) { _state.update { it.copy(date = d) }; loadSlots() }

    fun loadSlots() {
        val s = _state.value; val id = s.selectedId ?: return
        val gen = ++slotsGen                       // a slow response for a previous date/outlet must not overwrite newer data
        _state.update { it.copy(slotsLoading = true, slotsError = null) }
        viewModelScope.launch {
            repo.getSlots(id, s.date).fold(
                onSuccess = { l -> if (gen == slotsGen) _state.update { it.copy(slotsLoading = false, slots = l) } },
                onFailure = { e -> if (gen == slotsGen) _state.update { it.copy(slotsLoading = false, slots = emptyList(), slotsError = vendorErrorMessage(e)) } }
            )
        }
    }

    fun openNewSlot() = _state.update { it.copy(dialog = SlotDialog(capacityText = "20", startText = "12:00", endText = "12:30")) }
    fun openEditSlot(slot: VendorSlot) = _state.update { it.copy(dialog = SlotDialog(capacityText = slot.capacity.toString(), editing = slot)) }
    fun closeDialog() = _state.update { it.copy(dialog = null) }
    /** Field edits clear any previous error. */
    fun dialog(f: (SlotDialog) -> SlotDialog) = _state.update { s -> s.copy(dialog = s.dialog?.let { f(it).copy(error = null) }) }
    private fun dialogError(m: String) = _state.update { s -> s.copy(dialog = s.dialog?.copy(error = m)) }
    fun askDelete(slot: VendorSlot?) = _state.update { it.copy(deleting = slot) }

    fun submitDialog() {
        val s = _state.value; val d = s.dialog ?: return; val outletId = s.selectedId ?: return
        if (s.slotBusy) return
        val cap = d.capacityText.toIntOrNull()
        if (cap == null) { dialogError("Enter a whole number"); return }
        val edit = d.editing
        if (edit != null) {
            VendorRules.validateCapacityChange(edit, cap)?.let { m -> dialogError(m); return }
        } else {
            val st = runCatching { LocalTime.parse(d.startText) }.getOrNull(); val en = runCatching { LocalTime.parse(d.endText) }.getOrNull()
            if (st == null || en == null) { dialogError("Times must look like 12:30"); return }
            VendorRules.validateNewSlot(s.date, st, en, cap, s.slots)?.let { m -> dialogError(m); return }
        }
        _state.update { it.copy(slotBusy = true) }
        viewModelScope.launch {
            val r = if (edit != null) repo.setSlotCapacity(edit, cap)
                    else repo.createSlot(outletId, s.date, LocalTime.parse(d.startText), LocalTime.parse(d.endText), cap)
            r.fold(
                onSuccess = { _state.update { it.copy(slotBusy = false, dialog = null, message = if (edit != null) "Capacity updated" else "Slot added") }; loadSlots() },
                onFailure = { e -> _state.update { it.copy(slotBusy = false, dialog = it.dialog?.copy(error = vendorErrorMessage(e))) }; if (e is ConflictException) loadSlots() }
            )
        }
    }

    fun confirmDelete() {
        val s = _state.value; val slot = s.deleting ?: return
        if (s.slotBusy) return
        _state.update { it.copy(slotBusy = true) }
        viewModelScope.launch {
            repo.deleteSlot(slot).fold(
                onSuccess = { _state.update { it.copy(slotBusy = false, deleting = null, message = "Slot removed") }; loadSlots() },
                onFailure = { e -> _state.update { it.copy(slotBusy = false, deleting = null, message = vendorErrorMessage(e)) }; loadSlots() }
            )
        }
    }
}

private val DAY_FMT = DateTimeFormatter.ofPattern("EEE d MMM")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VendorOutletScreen(onBack: () -> Unit, viewModel: VendorOutletViewModel = hiltViewModel()) {
    val st by viewModel.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(st.message) { st.message?.let { snack.showSnackbar(it); viewModel.dismissMessage() } }

    Scaffold(topBar = { GagTopBar(title = "Outlet & pickup slots", onBack = onBack) }, snackbarHost = { SnackbarHost(snack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        when {
            st.loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            st.loadError != null -> GagErrorScreen(message = st.loadError!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (st.outlets.size > 1) item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { st.outlets.forEach { o -> FilterChip(selected = st.selectedId == o.id, onClick = { viewModel.select(o.id) }, label = { Text(o.name) }) } }
                }
                val o = st.outlet
                if (o != null) {
                    item {
                        Text(o.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (!o.orderingStatus.acceptsOrders || !o.isActive) {
                            Spacer(Modifier.height(6.dp))
                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp)) {
                                Text(when {
                                    !o.isActive -> "This outlet is inactive. Contact management to reactivate it."
                                    o.orderingStatus == OutletOrderingStatus.DEACTIVATED -> "Ordering for this outlet has been deactivated by management."
                                    else -> "New orders are paused by management. Existing orders continue. Contact management for details."
                                }, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("Open for orders now", fontWeight = FontWeight.SemiBold); Text("Your own open/closed switch. Separate from management suspension.", style = MaterialTheme.typography.bodySmall) }
                            Switch(checked = st.isOpen, onCheckedChange = { v -> viewModel.edit { it.copy(isOpen = v) } }, enabled = !st.saving)
                        }
                    }
                    item { OutlinedTextField(st.description, { v -> viewModel.edit { it.copy(description = v.take(300)) } }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth(), enabled = !st.saving) }
                    item { OutlinedTextField(st.phone, { v -> viewModel.edit { it.copy(phone = v.take(20)) } }, label = { Text("Phone") }, singleLine = true, isError = st.phoneError != null,
                        supportingText = st.phoneError?.let { e -> { Text(e) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), enabled = !st.saving) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(st.openTime, { v -> viewModel.edit { it.copy(openTime = v.take(5)) } }, label = { Text("Opens (HH:mm)") }, singleLine = true, isError = st.hoursError != null, modifier = Modifier.weight(1f), enabled = !st.saving)
                            OutlinedTextField(st.closeTime, { v -> viewModel.edit { it.copy(closeTime = v.take(5)) } }, label = { Text("Closes (HH:mm)") }, singleLine = true, isError = st.hoursError != null, modifier = Modifier.weight(1f), enabled = !st.saving)
                        }
                        st.hoursError?.let { Text(it, color = GagError, style = MaterialTheme.typography.bodySmall) }
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ALL_DAYS.forEach { d -> FilterChip(selected = d in st.days, onClick = { viewModel.toggleDay(d) }, enabled = !st.saving, label = { Text(d) }) } }
                    }
                    item { Button(onClick = viewModel::saveOutlet, enabled = !st.saving && st.hoursError == null && st.phoneError == null, modifier = Modifier.fillMaxWidth()) { Text(if (st.saving) "Saving…" else "Save outlet") } }
                    item { HorizontalDivider(); Text("Pickup slots (India time)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    item {
                        val today = VendorRules.todayIst()
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (0..6).map { today.plusDays(it.toLong()) }.forEach { d -> FilterChip(selected = st.date == d, onClick = { viewModel.setDate(d) }, label = { Text(if (d == today) "Today" else d.format(DAY_FMT)) }) }
                        }
                    }
                    item { OutlinedButton(onClick = viewModel::openNewSlot, enabled = !st.slotBusy) { Text("Add a slot") } }
                    when {
                        st.slotsLoading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        st.slotsError != null -> item { Column { Text(st.slotsError!!, color = GagError); TextButton(onClick = viewModel::loadSlots) { Text("Retry") } } }
                        st.slots.isEmpty() -> item { Text("No slots for this day yet.", style = MaterialTheme.typography.bodyMedium) }
                        else -> items(st.slots, key = { it.id }) { slot -> SlotCard(slot, st.slotBusy, onEdit = { viewModel.openEditSlot(slot) }, onDelete = { viewModel.askDelete(slot) }) }
                    }
                }
            }
        }
    }

    st.dialog?.let { d ->
        AlertDialog(
            onDismissRequest = viewModel::closeDialog,
            title = { Text(if (d.editing == null) "Add slot · ${st.date.format(DAY_FMT)}" else "Change capacity") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (d.editing == null) {
                        OutlinedTextField(d.startText, { v -> viewModel.dialog { it.copy(startText = v.take(5)) } }, label = { Text("Start (HH:mm)") }, singleLine = true)
                        OutlinedTextField(d.endText, { v -> viewModel.dialog { it.copy(endText = v.take(5)) } }, label = { Text("End (HH:mm)") }, singleLine = true)
                    } else Text("${d.editing.start}–${d.editing.end} · ${d.editing.booked} booked", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(d.capacityText, { v -> viewModel.dialog { it.copy(capacityText = v.filter(Char::isDigit).take(3)) } }, label = { Text("Capacity (orders)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    d.error?.let { Text(it, color = GagError, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { Button(onClick = viewModel::submitDialog, enabled = !st.slotBusy) { Text("Save") } },
            dismissButton = { TextButton(onClick = viewModel::closeDialog) { Text("Cancel") } }
        )
    }
    st.deleting?.let { slot ->
        AlertDialog(
            onDismissRequest = { viewModel.askDelete(null) },
            title = { Text("Remove this slot?") }, text = { Text("${slot.start}–${slot.end} has no bookings. Students will no longer see it.") },
            confirmButton = { Button(onClick = viewModel::confirmDelete, enabled = !st.slotBusy) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { viewModel.askDelete(null) }) { Text("Keep") } }
        )
    }
}

@Composable
private fun SlotCard(slot: VendorSlot, busy: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val past = !VendorRules.canEditSlot(slot)
    Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${slot.start}–${slot.end}", fontWeight = FontWeight.Bold)
                Text("${slot.booked}/${slot.capacity} booked · ${VendorRules.slotStatus(slot.booked, slot.capacity).name.lowercase()}${if (past) " · past" else ""}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onEdit, enabled = !busy && !past) { Text("Capacity") }
            TextButton(onClick = onDelete, enabled = !busy && !past && slot.booked == 0) { Text("Remove") }
        }
    }
}
