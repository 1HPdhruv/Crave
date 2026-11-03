package com.srmfood.gag.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.srmfood.gag.MainActivity
import com.srmfood.gag.R
import com.srmfood.gag.core.constants.AppConstants
import com.srmfood.gag.domain.repository.PushTokenRepository
import com.srmfood.gag.domain.repository.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives pushes. Content comes only from the data fields described in [PushPayload]; a message that can't be
 * parsed is dropped. Respects the in-app "notifications" switch and the OS permission, and uses a stable
 * Android id per server notification so redelivery replaces rather than duplicates.
 */
@AndroidEntryPoint
class GagFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushTokens: PushTokenRepository
    @Inject lateinit var preferences: UserPreferencesRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { runCatching { pushTokens.onTokenRefreshed(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = PushPayload.parse(message.data) ?: return
        scope.launch {
            val enabled = runCatching { preferences.notificationsEnabled.first() }.getOrDefault(true)
            if (enabled) show(payload)
        }
    }

    private fun show(p: PushPayload) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val open = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            p.deepLink?.let { data = it }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(this, p.androidId, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, AppConstants.NOTIFICATION_CHANNEL_ORDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(p.title)
            .setContentText(p.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(p.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(this).notify(p.notificationId, p.androidId, notification)   // tag+id: redelivery replaces
    }
}
