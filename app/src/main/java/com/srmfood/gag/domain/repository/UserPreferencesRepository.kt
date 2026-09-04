package com.srmfood.gag.domain.repository

import kotlinx.coroutines.flow.Flow

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

interface UserPreferencesRepository {
    val themeMode: Flow<ThemeMode>
    val notificationsEnabled: Flow<Boolean>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setNotificationsEnabled(enabled: Boolean)
}
