package com.srmfood.gag.core.di

import android.util.Log
import com.srmfood.gag.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json
import java.net.URL
import javax.inject.Singleton

private const val TAG = "SupabaseModule"

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        val url   = BuildConfig.SUPABASE_URL.trim()
        val key   = BuildConfig.SUPABASE_ANON_KEY.trim()

        // Safe log — hostname and key metadata only, never the full key
        val hostname = try { URL(url).host } catch (e: Exception) { "(parse error)" }
        Log.i(TAG, "=== Supabase Init ===")
        Log.i(TAG, "Host      : $hostname")
        Log.i(TAG, "Key set   : ${key.isNotBlank()}")
        Log.i(TAG, "Key prefix: ${key.take(4)}***")
        Log.i(TAG, "====================")

        // Runtime guard: legacy anon JWTs always start with "eyJ"
        if (key.isBlank()) {
            error("SUPABASE_ANON_KEY is empty. Add it to local.properties.")
        }
        if (!key.startsWith("eyJ")) {
            Log.w(TAG, "SUPABASE_ANON_KEY does not appear to be a legacy JWT (should start with 'eyJ'). " +
                    "Go to Supabase Dashboard → Project Settings → API → 'anon public' and copy the full JWT.")
        }

        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key
        ) {
            defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Functions)
        }
    }

    @Provides
    @Singleton
    fun provideSupabaseAuth(client: SupabaseClient): Auth = client.auth

    @Provides
    @Singleton
    fun provideSupabasePostgrest(client: SupabaseClient): Postgrest = client.postgrest

    @Provides
    @Singleton
    fun provideSupabaseFunctions(client: SupabaseClient): io.github.jan.supabase.functions.Functions = client.functions
}
