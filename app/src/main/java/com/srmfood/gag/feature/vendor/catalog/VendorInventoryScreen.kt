package com.srmfood.gag.feature.vendor.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
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
import com.srmfood.gag.core.ui.component.GagEmptyScreen
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.GagError
import com.srmfood.gag.domain.model.vendor.*
import com.srmfood.gag.domain.repository.VendorCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class InventoryFilter(val label: String) { ALL("All"), ATTENTION("Needs attention"), OUT("Out of stock") }

data class VendorInventoryState(
    val loading: Boolean = true,
    val error: String? = null,           // load failure with nothing to show
    val rows: List<VendorInventoryRow> = emptyList(),
    val filter: InventoryFilter = InventoryFilter.ALL,
    val editing: VendorInventoryRow? = null,
    val qtyText: String = "",
    val lowText: String = "",
    val busy: Boolean = false,
    val message: String? = null          // transient write result / failure
) {
    val visible get() = when (filter) {
        InventoryFilter.ALL -> rows
        InventoryFilter.ATTENTION -> rows.filter { it.stockStatus == StockStatus.LOW || it.stockStatus == StockStatus.OUT_OF_STOCK || it.stockStatus == StockStatus.NOT_TRACKED }
        InventoryFilter.OUT -> rows.filter { it.stockStatus == StockStatus.OUT_OF_STOCK }
    }
}

@HiltViewModel
class VendorInventoryViewModel @Inject constructor(private val repo: VendorCatalogRepository) : ViewModel() {
    private val _state = MutableStateFlow(VendorInventoryState())
    val state: StateFlow<VendorInventoryState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val hadRows = _state.value.rows.isNotEmpty()
            if (!hadRows) _state.update { it.copy(loading = true, error = null) }
            repo.getInventory().fold(
                onSuccess = { r -> _state.update { it.copy(loading = false, error = null, rows = r) } },
                // keep showing the last good list on a refresh failure, but say so
                onFailure = { e -> _state.update { if (hadRows) it.copy(loading = false, message = "Couldn't refresh: ${vendorErrorMessage(e)}") else it.copy(loading = false, error = vendorErrorMessage(e)) } }
            )
        }
    }

    fun setFilter(f: InventoryFilter) = _state.update { it.copy(filter = f) }
    fun startEdit(r: VendorInventoryRow) = _state.update { it.copy(editing = r, qtyText = if (r.stockStatus == StockStatus.NOT_TRACKED) "" else r.quantity.toString(), lowText = r.lowStockThreshold.toString(), message = null) }
    fun cancelEdit() = _state.update { it.copy(editing = null) }
    fun setQty(v: String) = _state.update { it.copy(qtyText = v.filter(Char::isDigit).take(6)) }
    fun setLow(v: String) = _state.update { it.copy(lowText = v.filter(Char::isDigit).take(5)) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun saveEdit() {
        val s = _state.value; val row = s.editing ?: return
        if (s.busy) return
        val qErr = VendorRules.validateStock(s.qtyText); val low = s.lowText.toIntOrNull()
        if (qErr != null || low == null) { _state.update { it.copy(message = qErr ?: "Enter a low-stock level") }; return }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.setStock(row.inventoryId, row.quantity, s.qtyText.toInt(), low).fold(
                onSuccess = { _state.update { it.copy(busy = false, editing = null, message = "Stock updated") }; load() },
                onFailure = { e -> _state.update { it.copy(busy = false, editing = null, message = vendorErrorMessage(e)) }; load() }
            )
        }
    }

    fun toggleAvailability(r: VendorInventoryRow) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.setFoodAvailability(r.foodItemId, !r.isAvailable).fold(
                onSuccess = { _state.update { it.copy(busy = false) }; load() },
                onFailure = { e -> _state.update { it.copy(busy = false, message = vendorErrorMessage(e)) }; load() }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VendorInventoryScreen(onBack: () -> Unit, viewModel: VendorInventoryViewModel = hiltViewModel()) {
    val st by viewModel.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(st.message) { st.message?.let { snack.showSnackbar(it); viewModel.dismissMessage() } }

    Scaffold(
        topBar = { GagTopBar(title = "Inventory", onBack = onBack) },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            st.loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            st.error != null -> GagErrorScreen(message = st.error!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            st.rows.isEmpty() -> GagEmptyScreen(title = "No menu items yet", message = "Add items under Manage Menu first.", icon = Icons.Outlined.Inventory2, modifier = Modifier.padding(padding))
            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InventoryFilter.entries.forEach { f -> FilterChip(selected = st.filter == f, onClick = { viewModel.setFilter(f) }, label = { Text(f.label) }) }
                }
                if (st.visible.isEmpty()) Text("Nothing in this view.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(st.visible, key = { it.inventoryId }) { r -> InventoryCard(r, st.busy, onEdit = { viewModel.startEdit(r) }, onToggle = { viewModel.toggleAvailability(r) }) }
                }
            }
        }
    }

    st.editing?.let { r ->
        AlertDialog(
            onDismissRequest = viewModel::cancelEdit,
            title = { Text(r.foodName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.stockStatus == StockStatus.NOT_TRACKED) Text("Stock isn't being tracked yet (unlimited). Enter the real quantity to start tracking.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = st.qtyText, onValueChange = viewModel::setQty, label = { Text("Quantity in stock") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = st.lowText, onValueChange = viewModel::setLow, label = { Text("Warn when at or below") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Text("If a student orders while you edit, the save is refused instead of overwriting their order.", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = { Button(onClick = viewModel::saveEdit, enabled = !st.busy && VendorRules.validateStock(st.qtyText) == null && st.lowText.isNotEmpty()) { Text("Save") } },
            dismissButton = { TextButton(onClick = viewModel::cancelEdit) { Text("Cancel") } }
        )
    }
}

@Composable
private fun InventoryCard(r: VendorInventoryRow, busy: Boolean, onEdit: () -> Unit, onToggle: () -> Unit) {
    val (label, color) = when (r.stockStatus) {
        StockStatus.OUT_OF_STOCK -> "Out of stock" to GagError
        StockStatus.LOW -> "Low: ${r.quantity} left" to MaterialTheme.colorScheme.tertiary
        StockStatus.OK -> "${r.quantity} in stock" to MaterialTheme.colorScheme.onSurface
        StockStatus.NOT_TRACKED -> "Not tracked (unlimited)" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.foodName, fontWeight = FontWeight.Bold)
                Text(label, color = color, style = MaterialTheme.typography.bodySmall)
                if (!r.isAvailable) Text("Hidden from students", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onEdit, enabled = !busy) { Text("Edit stock") }
            Switch(checked = r.isAvailable, onCheckedChange = { onToggle() }, enabled = !busy)
        }
    }
}
