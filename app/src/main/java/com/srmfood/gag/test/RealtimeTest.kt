package com.srmfood.gag.test

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RealtimeTest(private val client: SupabaseClient) {
    fun test(orderId: String): Flow<String> {
        val channel = client.realtime.channel("orders")
        val changes = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "orders"
            filter = "id=eq.$orderId"
        }
        return changes.map { it.record["status"]?.toString() ?: "" }
    }
}
