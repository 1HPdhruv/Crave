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

        // Fail closed: a flavor with a pinned project ref may only talk to that project
        // (staging must never silently reach production, and vice versa).
        val expectedRef = BuildConfig.EXPECTED_SUPABASE_REF
        if (expectedRef.isNotEmpty() && hostname != "$expectedRef.supabase.co") {
            error("Refusing to start: ${BuildConfig.FLAVOR} build requires project '$expectedRef' " +
                "but SUPABASE_URL host is '$hostname'. Set SUPABASE_URL/SUPABASE_ANON_KEY (or STAGING_SUPABASE_URL/" +
                "STAGING_SUPABASE_ANON_KEY) in local.properties to that project and rebuild.")
        }

        // Runtime guard: legacy anon JWTs always start with "eyJ"
        if (key.isBlank()) {
            error("SUPABASE_ANON_KEY is empty. Add it to local.properties.")
        }
        if (!key.startsWith("eyJ") && !key.startsWith("sb_publishable_")) {
            Log.w(TAG, "SUPABASE_ANON_KEY is neither a legacy anon JWT nor an sb_publishable_ key; requests will likely be rejected.")
        }

        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key
        ) {
            defaultSerializer = KotlinXSerializer(Json { 
                ignoreUnknownKeys = true 
                encodeDefaults = true
            })
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
