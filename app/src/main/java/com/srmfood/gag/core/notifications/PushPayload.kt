package com.srmfood.gag.core.notifications

import android.net.Uri

/**
 * The parsed content of a push (FCM *data* message). Pure logic, unit-tested.
 *
 * Server contract (see supabase/drafts_unapproved/send-push): data = {
 *   notification_id: <notifications.id>, title, body, type?, order_id?, ticket_id?
 * }.
 * Only these fields are ever used. The server never sends internal notes (they never reach the
 * `notifications` table), and the client additionally never shows anything it can't map to a known destination.
 */
data class PushPayload(
    val notificationId: String,
    val title: String,
    val body: String,
    val deepLink: Uri?
) {
    /** Stable Android notification id: the same server notification always maps to the same slot, so a redelivery replaces instead of stacking. */
    val androidId: Int get() = stableNotificationId(notificationId)

    companion object {
        private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        const val MAX_TITLE = 80
        const val MAX_BODY = 240

        /** Returns null when the message is unusable (no id / nothing to show) so it is dropped rather than shown half-empty. */
        fun parse(data: Map<String, String>): PushPayload? {
            val id = data["notification_id"]?.takeIf { UUID.matches(it) } ?: return null
            val title = data["title"]?.trim().orEmpty().take(MAX_TITLE)
            val body = data["body"]?.trim().orEmpty().take(MAX_BODY)
            if (title.isEmpty() && body.isEmpty()) return null
            val ticket = data["ticket_id"]?.takeIf { UUID.matches(it) }
            val order = data["order_id"]?.takeIf { UUID.matches(it) }
            val link = when {
                ticket != null -> Uri.parse("gag://tickets/$ticket")
                order != null -> Uri.parse("gag://orders/$order")
                else -> null
            }
            return PushPayload(id, title.ifEmpty { "CRAVE" }, body, link)
        }

        fun stableNotificationId(notificationId: String): Int = notificationId.hashCode() and 0x7FFFFFFF
    }
}
