package com.srmfood.gag.feature.vendor.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagPrimaryButton
import com.srmfood.gag.core.ui.component.GagTextField
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

const val NEW_FOOD_ID = "new"

data class VendorFoodEditState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val outlets: List<VendorOutlet> = emptyList(),
    val categories: List<FoodCategory> = emptyList(),
    val draft: VendorFoodDraft = VendorFoodDraft(),
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null,
    /** Non-null after a successful save; the screen then closes. */
    val savedMessage: String? = null
) {
    val errors get() = VendorRules.validateFood(draft)
    val isNew get() = draft.id == null
}

@HiltViewModel
class VendorFoodEditViewModel @Inject constructor(
    private val repo: VendorCatalogRepository,
    savedState: SavedStateHandle
) : ViewModel() {
    private val foodId: String = savedState["foodId"] ?: NEW_FOOD_ID
    private val _state = MutableStateFlow(VendorFoodEditState())
    val state: StateFlow<VendorFoodEditState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            val outlets = repo.getMyOutlets(); val cats = repo.getCategories()
            val err = (outlets.exceptionOrNull() ?: cats.exceptionOrNull())
            if (err != null) { _state.update { it.copy(loading = false, loadError = vendorErrorMessage(err)) }; return@launch }
            val o = outlets.getOrThrow(); val c = cats.getOrThrow()
            if (o.isEmpty()) { _state.update { it.copy(loading = false, loadError = "No outlet is linked to your account yet.") }; return@launch }
            val draft = if (foodId == NEW_FOOD_ID) VendorFoodDraft(outletId = o.first().id)
            else repo.getFood(foodId).getOrElse { e -> _state.update { it.copy(loading = false, loadError = vendorErrorMessage(e)) }; return@launch }
            _state.update { it.copy(loading = false, outlets = o, categories = c, draft = draft) }
        }
    }

    fun edit(f: (VendorFoodDraft) -> VendorFoodDraft) = _state.update { it.copy(draft = f(it.draft), saveError = null) }

    fun save() {
        val s = _state.value
        if (s.saving) return            // repeated taps can't send two writes
        if (s.errors.any) { _state.update { it.copy(showErrors = true) }; return }
        _state.update { it.copy(saving = true, saveError = null, showErrors = true) }
        viewModelScope.launch {
            repo.saveFood(s.draft).fold(
                onSuccess = {
                    _state.update { st -> st.copy(saving = false, savedMessage = if (s.isNew)
                        "Item created. New items start with unlimited stock — set a real quantity under Inventory." else "Changes saved.") }
                },
                onFailure = { e -> _state.update { it.copy(saving = false, saveError = vendorErrorMessage(e)) } }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VendorFoodEditScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: VendorFoodEditViewModel = hiltViewModel()
) {
    val st by viewModel.state.collectAsState()
    LaunchedEffect(st.savedMessage) { st.savedMessage?.let(onSaved) }

    Scaffold(
        topBar = { GagTopBar(title = if (st.isNew) "Add item" else "Edit item", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            st.loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            st.loadError != null -> GagErrorScreen(message = st.loadError!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> {
                val d = st.draft; val e = if (st.showErrors) st.errors else VendorRules.FoodErrors()
                Column(
                    Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (st.outlets.size > 1 && st.isNew) {
                        Text("Outlet", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            st.outlets.forEach { o -> FilterChip(selected = d.outletId == o.id, onClick = { viewModel.edit { it.copy(outletId = o.id) } }, label = { Text(o.name) }) }
                        }
                    }
                    GagTextField(value = d.name, onValueChange = { v -> viewModel.edit { it.copy(name = v) } }, label = "Name",
                        isError = e.name != null, errorMessage = e.name, enabled = !st.saving, modifier = Modifier.fillMaxWidth())
                    GagTextField(value = d.description, onValueChange = { v -> viewModel.edit { it.copy(description = v) } }, label = "Description",
                        isError = e.description != null, errorMessage = e.description, singleLine = false, maxLines = 4, enabled = !st.saving, modifier = Modifier.fillMaxWidth())
                    GagTextField(value = d.priceText, onValueChange = { v -> viewModel.edit { it.copy(priceText = v) } }, label = "Price (₹)",
                        isError = e.price != null, errorMessage = e.price, enabled = !st.saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    GagTextField(value = d.prepTimeText, onValueChange = { v -> viewModel.edit { it.copy(prepTimeText = v) } }, label = "Preparation time (minutes)",
                        isError = e.prep != null, errorMessage = e.prep, enabled = !st.saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        st.categories.forEach { c -> FilterChip(selected = d.categoryId == c.id, onClick = { viewModel.edit { it.copy(categoryId = c.id) } }, enabled = !st.saving, label = { Text("${c.emoji} ${c.name}".trim()) }) }
                    }
                    e.category?.let { Text(it, color = GagError, style = MaterialTheme.typography.bodySmall) }
                    GagTextField(value = d.imageUrl, onValueChange = { v -> viewModel.edit { it.copy(imageUrl = v) } }, label = "Image link (https://…, optional)",
                        isError = e.image != null, errorMessage = e.image, enabled = !st.saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Vegetarian", Modifier.weight(1f)); Switch(checked = d.isVeg, onCheckedChange = { v -> viewModel.edit { it.copy(isVeg = v) } }, enabled = !st.saving)
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Available to order", Modifier.weight(1f)); Switch(checked = d.isAvailable, onCheckedChange = { v -> viewModel.edit { it.copy(isAvailable = v) } }, enabled = !st.saving)
                    }
                    st.saveError?.let { Text(it, color = GagError) }
                    GagPrimaryButton(text = if (st.isNew) "Create item" else "Save changes", onClick = viewModel::save,
                        isLoading = st.saving, enabled = !st.saving, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
