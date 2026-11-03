package com.srmfood.gag.data.repository.supabase

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.srmfood.gag.domain.repository.PushTokenRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable private data class PushTokenParams(@SerialName("p_token") val token: String, @SerialName("p_platform") val platform: String = "android")
@Serializable private data class PushTokenOnly(@SerialName("p_token") val token: String)

/** Needs DRAFT_push_tokens.sql on the server; until then registration fails quietly and is retried on the next sign-in. */
@Singleton
class SupabasePushTokenRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: Auth,
    @ApplicationContext context: Context
) : PushTokenRepository {

    private val prefs = context.getSharedPreferences("push_token", Context.MODE_PRIVATE)
    private val lock = Any()

    private fun currentUserId(): String? = auth.currentSessionOrNull()?.user?.id

    override suspend fun onTokenRefreshed(token: String) {
        synchronized(lock) { prefs.edit().putString("token", token).remove("registered_for").commit() }
        if (currentUserId() != null) syncForCurrentUser()
    }

    private suspend fun fetchToken(): String = synchronized(lock) { prefs.getString("token", null) } ?: suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { t -> cont.resume(t) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }.also { t -> synchronized(lock) { prefs.edit().putString("token", t).commit() } }

    override suspend fun syncForCurrentUser(): Result<Unit> = runCatching {
        val uid = currentUserId() ?: return@runCatching
        val token = fetchToken()
        val marker = "$uid|${token.hashCode()}"
        if (synchronized(lock) { prefs.getString("registered_for", null) } == marker) return@runCatching   // already done
        postgrest.rpc("register_push_token", PushTokenParams(token))
        synchronized(lock) { prefs.edit().putString("registered_for", marker).commit() }
        Unit
    }.onFailure { android.util.Log.w("PushToken", "register failed: ${it.javaClass.simpleName}") }

    override suspend fun unregisterCurrent() {
        val token = synchronized(lock) { prefs.getString("token", null) }
        synchronized(lock) { prefs.edit().remove("registered_for").commit() }
        if (token == null || currentUserId() == null) return
        runCatching { postgrest.rpc("unregister_push_token", PushTokenOnly(token)) }
            .onFailure { android.util.Log.w("PushToken", "unregister failed: ${it.javaClass.simpleName}") }
    }
}
