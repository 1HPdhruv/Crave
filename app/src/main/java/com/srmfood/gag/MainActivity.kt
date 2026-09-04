package com.srmfood.gag

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
            val themeMode by userPreferencesRepository.themeMode.collectAsState(initial = com.srmfood.gag.domain.repository.ThemeMode.SYSTEM)
            val isDarkTheme = when (themeMode) {
                com.srmfood.gag.domain.repository.ThemeMode.LIGHT -> false
                com.srmfood.gag.domain.repository.ThemeMode.DARK -> true
                com.srmfood.gag.domain.repository.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
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
