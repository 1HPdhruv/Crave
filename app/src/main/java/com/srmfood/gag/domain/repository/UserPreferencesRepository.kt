package com.srmfood.gag.domain.repository

import kotlinx.coroutines.flow.Flow

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

interface UserPreferencesRepository {
    val themeMode: Flow<ThemeMode>
    val notificationsEnabled: Flow<Boolean>

    /**
     * Whether this DEVICE has finished onboarding. Deliberately not tied to an account: logging out or switching
     * accounts must not replay the intro, and clearing per-user caches must not reset it.
     */
    val onboardingComplete: Flow<Boolean>
    suspend fun setOnboardingComplete()

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setNotificationsEnabled(enabled: Boolean)
}
