package com.srmfood.gag.feature.support

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the idempotency key of an in-flight "create ticket" request so a retry
 * after a timeout, process death or app restart reuses it (the server returns
 * the original ticket instead of creating a second one).
 */
interface SupportRequestIdStore {
    /** Returns the stored id for [key], creating and persisting one if needed. */
    fun getOrCreate(key: String): String
    fun clear(key: String)
}

@Singleton
class SharedPrefsSupportRequestIdStore @Inject constructor(
    @ApplicationContext context: Context
) : SupportRequestIdStore {
    private val prefs = context.getSharedPreferences("support_request_ids", Context.MODE_PRIVATE)

    @Synchronized
    override fun getOrCreate(key: String): String {
        prefs.getString(key, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(key, id).commit()
        return id
    }

    @Synchronized
    override fun clear(key: String) {
        prefs.edit().remove(key).commit()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SupportModule {
    @Binds
    abstract fun bindRequestIdStore(impl: SharedPrefsSupportRequestIdStore): SupportRequestIdStore
}
