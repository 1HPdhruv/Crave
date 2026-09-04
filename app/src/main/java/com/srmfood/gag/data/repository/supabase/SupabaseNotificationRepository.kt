package com.srmfood.gag.data.repository.supabase

import com.srmfood.gag.data.remote.dto.NotificationDto
import com.srmfood.gag.domain.model.Notification
import com.srmfood.gag.domain.model.toDomain
import com.srmfood.gag.domain.repository.NotificationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseNotificationRepository @Inject constructor(
    private val client: SupabaseClient,
    private val auth: Auth,
    private val postgrest: Postgrest
) : NotificationRepository {

    override fun getNotifications(): Flow<List<Notification>> = callbackFlow {
        val session = auth.currentSessionOrNull()
        val userId = session?.user?.id
        
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // Fetch initial list of notifications
        launch {
            try {
                val initialDtos = postgrest["notifications"]
                    .select() {
                        filter { eq("user_id", userId) }
                    }.decodeList<NotificationDto>()
                
                trySend(initialDtos.map { it.toDomain() }.sortedByDescending { it.createdAt })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Setup realtime subscription
        val channel = client.realtime.channel("public:notifications")
        val changes = channel.postgresChangeFlow<PostgresAction>("public") {
            table = "notifications"
            filter = "user_id=eq.$userId"
        }

        val job = launch {
            changes.collect { action ->
                try {
                    // On any insert/update, refetch the full list (simple and reliable)
                    val dtos = postgrest["notifications"]
                        .select() {
                            filter { eq("user_id", userId) }
                        }.decodeList<NotificationDto>()
                    
                    trySend(dtos.map { it.toDomain() }.sortedByDescending { it.createdAt })
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        channel.subscribe()
        
        awaitClose {
            GlobalScope.launch { channel.unsubscribe() }
            job.cancel()
        }
    }

    override suspend fun markAsRead(notificationId: String): Result<Unit> = runCatching {
        val session = auth.currentSessionOrNull()
        val userId = session?.user?.id ?: throw Exception("User not logged in")

        postgrest["notifications"].update(
            { set("is_read", true) }
        ) {
            filter { 
                eq("id", notificationId)
                eq("user_id", userId) 
            }
        }
    }
}
