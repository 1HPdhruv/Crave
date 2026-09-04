package com.srmfood.gag.feature.vendor.scanner

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagPrimaryButton
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.usecase.order.ConfirmPickupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QRScannerViewModel @Inject constructor(
    private val confirmPickupUseCase: ConfirmPickupUseCase
) : ViewModel() {

    private val _scanState = MutableStateFlow<UiState<Order>>(UiState.Idle)
    val scanState: StateFlow<UiState<Order>> = _scanState.asStateFlow()

    fun processQRCode(qrToken: String) {
        viewModelScope.launch {
            _scanState.value = UiState.Loading
            val result = confirmPickupUseCase(qrToken)
            _scanState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Failed to verify QR") }
            )
        }
    }

    fun resetState() { _scanState.value = UiState.Idle }
}

@Composable
fun QRScannerScreen(
    onBack: () -> Unit,
    viewModel: QRScannerViewModel = hiltViewModel()
) {
    val scanState by viewModel.scanState.collectAsState()
    var hasCameraPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = { GagTopBar(title = "Scan Pickup QR", onBack = onBack) },
        containerColor = GagBackground
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!hasCameraPermission) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Camera permission is required to scan QR codes.", textAlign = TextAlign.Center, color = GagOnSurfaceVariant)
                }
            } else {
                // In a real app, integrate CameraX + ML Kit Barcode Scanning here.
                // For this UI mockup, we'll provide a manual fallback button to simulate a scan.
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().background(GagSurfaceVariant, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(64.dp), tint = GagOnSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Point camera at student's QR code", style = MaterialTheme.typography.bodyMedium, color = GagOnSurfaceVariant)
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        var tokenInput by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("Enter or Paste QR Token") },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        GagPrimaryButton(
                            text = "Validate Token",
                            onClick = { if (tokenInput.isNotBlank()) viewModel.processQRCode(tokenInput.trim()) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (val state = scanState) {
                is UiState.Loading -> CircularProgressIndicator(color = GagOrange)
                is UiState.Error -> {
                    Surface(shape = RoundedCornerShape(12.dp), color = GagErrorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(state.message, color = GagError, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    GagPrimaryButton(text = "Scan Again", onClick = viewModel::resetState)
                }
                is UiState.Success -> {
                    val order = state.data
                    Surface(shape = RoundedCornerShape(16.dp), color = GagSuccessContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Order Verified!", style = MaterialTheme.typography.titleMedium, color = GagSuccess, fontWeight = FontWeight.Bold)
                            Text("Order: ${order.orderNumber}", style = MaterialTheme.typography.bodyMedium, color = GagOnBackground)
                            Text("Hand over to student.", style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    GagPrimaryButton(text = "Scan Next", onClick = viewModel::resetState)
                }
                else -> {}
            }
        }
    }
}
