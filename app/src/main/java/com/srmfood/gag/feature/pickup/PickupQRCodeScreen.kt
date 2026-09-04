package com.srmfood.gag.feature.pickup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.usecase.order.GetQrTokenUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────
@HiltViewModel
class PickupQRViewModel @Inject constructor(
    private val getQrTokenUseCase: GetQrTokenUseCase
) : ViewModel() {

    private val _qrState = MutableStateFlow<UiState<String>>(UiState.Loading)
    val qrState: StateFlow<UiState<String>> = _qrState.asStateFlow()

    fun loadQR(orderId: String) {
        viewModelScope.launch {
            _qrState.value = UiState.Loading
            val result = getQrTokenUseCase(orderId)
            _qrState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Failed to load QR") }
            )
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun PickupQRCodeScreen(
    orderId: String,
    onBack: () -> Unit,
    viewModel: PickupQRViewModel = hiltViewModel()
) {
    val qrState by viewModel.qrState.collectAsState()

    LaunchedEffect(orderId) { viewModel.loadQR(orderId) }

    Scaffold(
        topBar = { GagTopBar(title = "Pickup QR Code", onBack = onBack) },
        containerColor = GagBackground
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val state = qrState) {
                is UiState.Loading -> GagLoadingScreen()
                is UiState.Error -> {
                    Icon(Icons.Default.ErrorOutline, null, tint = GagError, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(state.message, color = GagError, textAlign = TextAlign.Center)
                }
                is UiState.Success -> {
                    // QR code image
                    val qrBitmap = remember(state.data) { generateQRBitmap(state.data) }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            qrBitmap?.let { bmp ->
                                androidx.compose.foundation.Image(
                                    bitmap = bmp,
                                    contentDescription = "Pickup QR Code",
                                    modifier = Modifier.size(220.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                    Icon(Icons.Default.CheckCircle, null, tint = GagSuccess, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Show this QR to the outlet staff", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("This QR code is valid for 10 minutes", style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))

                    Surface(shape = RoundedCornerShape(12.dp), color = GagSurfaceVariant) {
                        Text(
                            text = state.data.takeLast(12),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Mono,
                            color = GagOnSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

private fun generateQRBitmap(content: String): ImageBitmap? {
    return try {
        val writer = QRCodeWriter()
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
        for (x in 0 until 512) {
            for (y in 0 until 512) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

private val FontWeight.Companion.Mono: FontWeight get() = W500
