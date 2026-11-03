package com.srmfood.gag.domain.repository

/**
 * Associates this device's FCM token with the signed-in user (server-side table; see DRAFT_push_tokens.sql).
 * The token is always bound to the CURRENT session's user: on account switch the server moves it, on logout it is removed.
 */
interface PushTokenRepository {
    /** Called whenever Firebase issues a new token. Remembered locally and synced if someone is signed in. */
    suspend fun onTokenRefreshed(token: String)

    /** Fetches the current token and registers it for the signed-in user if that isn't already the case. */
    suspend fun syncForCurrentUser(): Result<Unit>

    /** Removes this device's token from the signed-in user (call BEFORE signing out). Never throws. */
    suspend fun unregisterCurrent()
}
