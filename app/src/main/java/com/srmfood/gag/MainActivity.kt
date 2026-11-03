package com.srmfood.gag

import kotlinx.coroutines.flow.first
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.srmfood.gag.core.payment.RazorpayManager
import com.srmfood.gag.core.ui.theme.GagBackground
import com.srmfood.gag.core.ui.theme.GagTheme
import com.srmfood.gag.navigation.GagNavGraph
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    @Inject
    lateinit var razorpayManager: RazorpayManager
    
    @Inject
    lateinit var userPreferencesRepository: com.srmfood.gag.domain.repository.UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NotificationPermissionEffect()
            val themeMode by userPreferencesRepository.themeMode.collectAsState(initial = com.srmfood.gag.domain.repository.ThemeMode.SYSTEM)
            val isDarkTheme = when (themeMode) {
                com.srmfood.gag.domain.repository.ThemeMode.LIGHT -> false
                com.srmfood.gag.domain.repository.ThemeMode.DARK -> true
                // Website is paper-light only, so "System" resolves to light; Dark is opt-in.
                com.srmfood.gag.domain.repository.ThemeMode.SYSTEM -> false
            }
            
            GagTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    GagNavGraph()
                }
            }
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, data: PaymentData?) {
        CoroutineScope(Dispatchers.Main).launch {
            razorpayManager.onPaymentSuccess(razorpayPaymentId, data)
        }
    }

    override fun onPaymentError(code: Int, message: String?, data: PaymentData?) {
        CoroutineScope(Dispatchers.Main).launch {
            razorpayManager.onPaymentError(code, message, data)
        }
    }
}

/**
 * Asks for the Android 13+ notification permission once, and only after the user is signed in (so the prompt has
 * context). A refusal is remembered and not repeated; the in-app Notifications screen keeps working without it.
 */
@androidx.compose.runtime.Composable
private fun NotificationPermissionEffect() {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = androidx.compose.runtime.remember { context.getSharedPreferences("permissions", android.content.Context.MODE_PRIVATE) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        prefs.edit().putBoolean("notif_asked", true).apply()
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted && !prefs.getBoolean("notif_asked", false)) {
            // Wait until a session exists: the first screen before login shouldn't open with a system dialog.
            val auth = (context.applicationContext as GagApplication).auth
            auth.sessionStatus.first { it is io.github.jan.supabase.gotrue.SessionStatus.Authenticated }
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
