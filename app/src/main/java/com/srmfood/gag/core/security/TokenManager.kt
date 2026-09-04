package com.srmfood.gag.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.srmfood.gag.core.constants.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages authentication tokens using EncryptedSharedPreferences.
 *
 * Security principles:
 * - Uses AES256 encryption via AndroidKeyStore
 * - Never logs token values
 * - Provides atomic clear operation for logout
 * - refreshTokenSync is blocking/sync for use in OkHttp interceptor
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "gag_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        encryptedPrefs.edit()
            .putString(AppConstants.PREF_ACCESS_TOKEN, accessToken)
            .putString(AppConstants.PREF_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun getAccessToken(): String? =
        encryptedPrefs.getString(AppConstants.PREF_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? =
        encryptedPrefs.getString(AppConstants.PREF_REFRESH_TOKEN, null)

    fun saveUserInfo(userId: String, userName: String, email: String, role: String) {
        encryptedPrefs.edit()
            .putString(AppConstants.PREF_USER_ID, userId)
            .putString(AppConstants.PREF_USER_NAME, userName)
            .putString(AppConstants.PREF_USER_EMAIL, email)
            .putString(AppConstants.PREF_USER_ROLE, role)
            .apply()
    }

    fun getUserId(): String? = encryptedPrefs.getString(AppConstants.PREF_USER_ID, null)
    fun getUserName(): String? = encryptedPrefs.getString(AppConstants.PREF_USER_NAME, null)
    fun getUserEmail(): String? = encryptedPrefs.getString(AppConstants.PREF_USER_EMAIL, null)
    fun getUserRole(): String? = encryptedPrefs.getString(AppConstants.PREF_USER_ROLE, null)

    fun isLoggedIn(): Boolean = getAccessToken() != null

    fun clearTokens() {
        encryptedPrefs.edit()
            .remove(AppConstants.PREF_ACCESS_TOKEN)
            .remove(AppConstants.PREF_REFRESH_TOKEN)
            .remove(AppConstants.PREF_USER_ID)
            .remove(AppConstants.PREF_USER_NAME)
            .remove(AppConstants.PREF_USER_EMAIL)
            .remove(AppConstants.PREF_USER_ROLE)
            .apply()
    }

    /**
     * Synchronous refresh for OkHttp interceptor use.
     * Do NOT call from main thread.
     * Returns new access token or null if refresh failed.
     */
    fun refreshTokenSync(): String? {
        // TODO: Implement actual token refresh via Retrofit when backend is ready.
        // This requires a separate OkHttp client without the auth interceptor
        // to avoid circular interceptor calls.
        return null
    }
}
