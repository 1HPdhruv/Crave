package com.srmfood.gag

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.srmfood.gag.BuildConfig
import com.srmfood.gag.core.constants.AppConstants
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import java.net.URL

@HiltAndroidApp
class GagApplication : Application() {

    @javax.inject.Inject lateinit var auth: io.github.jan.supabase.gotrue.Auth
    @javax.inject.Inject lateinit var pushTokens: com.srmfood.gag.domain.repository.PushTokenRepository

    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        logStartupConfig()
        createNotificationChannels()
        registerPushTokenOnSignIn()
    }

    /**
     * Binds this device's FCM token to whoever is signed in: fires on cold start with a restored session, on login,
     * and on account switch (the server moves the token). Failures are retried on the next sign-in/token refresh.
     */
    private fun registerPushTokenOnSignIn() {
        appScope.launch {
            auth.sessionStatus.collect { status ->
                if (status is io.github.jan.supabase.gotrue.SessionStatus.Authenticated) {
                    runCatching { pushTokens.syncForCurrentUser() }
                }
            }
        }
    }

    private fun logStartupConfig() {
        val supabaseHostname = try {
            URL(BuildConfig.SUPABASE_URL).host
        } catch (e: Exception) {
            "(invalid url)"
        }
        // NOTE: SUPABASE_ANON_KEY is intentionally NOT logged
        Log.i("GagApp", "=== Startup Config ===")
        Log.i("GagApp", "USE_MOCK        : ${BuildConfig.USE_MOCK}")
        Log.i("GagApp", "SUPABASE_HOST   : $supabaseHostname")
        Log.i("GagApp", "BUILD_TYPE      : ${BuildConfig.BUILD_TYPE}")
        Log.i("GagApp", "FLAVOR          : ${BuildConfig.FLAVOR}")
        Log.i("GagApp", "======================")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val ordersChannel = NotificationChannel(
                AppConstants.NOTIFICATION_CHANNEL_ORDERS,
                getString(R.string.order_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.order_notification_channel_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            val promosChannel = NotificationChannel(
                AppConstants.NOTIFICATION_CHANNEL_PROMOS,
                "Promotions",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            manager.createNotificationChannels(listOf(ordersChannel, promosChannel))
        }
    }
}
