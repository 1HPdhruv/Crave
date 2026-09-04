package com.srmfood.gag.feature.outlets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagEmptyScreen
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.Outlet
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.domain.usecase.outlet.GetOutletsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class OutletListViewModel @Inject constructor(
    private val getOutletsUseCase: GetOutletsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Outlet>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Outlet>>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getOutletsUseCase().collectLatest { outlets ->
                _uiState.value = if (outlets.isEmpty()) UiState.Empty else UiState.Success(outlets)
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun OutletListScreen(
    onBack: () -> Unit,
    onOutletClick: (String) -> Unit,
    viewModel: OutletListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { GagTopBar(title = "All Outlets", onBack = onBack) },
        containerColor = GagBackground
    ) { padding ->
        when (uiState) {
            is UiState.Loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            is UiState.Empty -> GagEmptyScreen(title = "No outlets available", modifier = Modifier.padding(padding))
            is UiState.Error -> GagErrorScreen(message = (uiState as UiState.Error).message, modifier = Modifier.padding(padding))
            is UiState.Success -> {
                val outlets = (uiState as UiState.Success<List<Outlet>>).data
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(outlets, key = { it.id }) { outlet ->
                        OutletListItem(outlet = outlet, onClick = { onOutletClick(outlet.id) })
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun OutletListItem(outlet: Outlet, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GagSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)).background(GagSurfaceVariant)
            ) {
                AsyncImage(model = outlet.imageUrl, contentDescription = outlet.name, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    color = if (outlet.isOpen) GagSuccessContainer else GagErrorContainer,
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = if (outlet.isOpen) "Open" else "Closed",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (outlet.isOpen) GagSuccess else GagError,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(outlet.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(outlet.description, style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, null, tint = GagOnSurfaceVariant, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(outlet.location.building, style = MaterialTheme.typography.labelSmall, color = GagOnSurfaceVariant)
                    }
                    if (outlet.isOpen && outlet.estimatedWaitMinutes > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, null, tint = GagAmber, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("~${outlet.estimatedWaitMinutes}m", style = MaterialTheme.typography.labelSmall, color = GagOnSurfaceVariant)
                        }
                    }
                    if (outlet.rating > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, null, tint = GagAmber, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(outlet.rating.toString(), style = MaterialTheme.typography.labelSmall, color = GagOnSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
