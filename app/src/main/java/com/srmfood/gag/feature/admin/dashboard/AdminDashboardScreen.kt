package com.srmfood.gag.feature.admin.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Logout
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
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.Outlet
import com.srmfood.gag.domain.usecase.admin.GetSystemStatsUseCase
import com.srmfood.gag.domain.usecase.admin.SystemStats
import com.srmfood.gag.domain.usecase.admin.ToggleOutletStatusUseCase
import com.srmfood.gag.domain.usecase.outlet.GetOutletsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val getSystemStatsUseCase: GetSystemStatsUseCase,
    private val getOutletsUseCase: GetOutletsUseCase,
    private val toggleOutletStatusUseCase: ToggleOutletStatusUseCase
) : ViewModel() {
    private val _stats = MutableStateFlow<UiState<SystemStats>>(UiState.Loading)
    val stats: StateFlow<UiState<SystemStats>> = _stats.asStateFlow()

    private val _outlets = MutableStateFlow<UiState<List<Outlet>>>(UiState.Loading)
    val outlets: StateFlow<UiState<List<Outlet>>> = _outlets.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val res = getSystemStatsUseCase()
            _stats.value = res.fold(onSuccess = { UiState.Success(it) }, onFailure = { UiState.Error(it.message ?: "Error") })
        }
        viewModelScope.launch {
            getOutletsUseCase().collectLatest {
                _outlets.value = UiState.Success(it)
            }
        }
    }

    fun toggleOutletStatus(outletId: String, isOpen: Boolean) {
        viewModelScope.launch {
            toggleOutletStatusUseCase(outletId, isOpen)
            // Re-fetch handled via flow
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onLogout: () -> Unit,
    viewModel: AdminDashboardViewModel = hiltViewModel()
) {
    val statsState by viewModel.stats.collectAsState()
    val outletsState by viewModel.outlets.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Console", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = onLogout) { Icon(Icons.Outlined.Logout, "Logout", tint = GagError) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GagBackground)
            )
        },
        containerColor = GagBackground
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                when (val state = statsState) {
                    is UiState.Loading -> CircularProgressIndicator()
                    is UiState.Success -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatBox("Total Users", state.data.totalUsers.toString(), modifier = Modifier.weight(1f))
                                StatBox("Total Vendors", state.data.totalVendors.toString(), modifier = Modifier.weight(1f))
                                StatBox("Total Outlets", state.data.totalOutlets.toString(), modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatBox("Total Orders", state.data.totalOrders.toString(), modifier = Modifier.weight(1f))
                                StatBox("Active Orders", state.data.activeOrders.toString(), modifier = Modifier.weight(1f))
                                StatBox("Completed Orders", state.data.completedOrders.toString(), modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatBox("Total Revenue", "₹${state.data.revenue.toInt()}", modifier = Modifier.weight(1f))
                                StatBox("Orders Today", state.data.ordersToday.toString(), modifier = Modifier.weight(1f))
                                StatBox("Revenue Today", "₹${state.data.revenueToday.toInt()}", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    else -> {}
                }
            }
            item { Text("Manage Outlets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

            when (val state = outletsState) {
                is UiState.Loading -> item { GagLoadingScreen() }
                is UiState.Success -> {
                    items(state.data, key = { it.id }) { outlet ->
                        Surface(shape = RoundedCornerShape(12.dp), color = GagSurface, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(outlet.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(outlet.location.building, style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                                }
                                Switch(
                                    checked = outlet.isOpen,
                                    onCheckedChange = { viewModel.toggleOutletStatus(outlet.id, it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = GagSuccess, checkedTrackColor = GagSuccessContainer)
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = GagSurfaceVariant, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GagOrange)
            Text(label, style = MaterialTheme.typography.labelSmall, color = GagOnSurfaceVariant)
        }
    }
}
