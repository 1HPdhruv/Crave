package com.srmfood.gag.feature.auth.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.domain.model.UserRole
import com.srmfood.gag.domain.usecase.auth.GetUserRoleUseCase
import com.srmfood.gag.domain.usecase.auth.IsLoggedInUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SplashState(
    val isReady: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userRole: UserRole? = null,
    val onboardingComplete: Boolean = false
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val isLoggedInUseCase: IsLoggedInUseCase,
    private val getUserRoleUseCase: GetUserRoleUseCase,
    private val preferences: com.srmfood.gag.domain.repository.UserPreferencesRepository
) : ViewModel() {

    /** Persist completion first, then the caller navigates; also updates the in-memory state. */
    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { preferences.setOnboardingComplete() }   // even if saving fails, let the user continue
            _splashState.value = _splashState.value.copy(onboardingComplete = true)
            onDone()
        }
    }

    private val _splashState = MutableStateFlow(SplashState())
    val splashState: StateFlow<SplashState> = _splashState.asStateFlow()

    init {
        viewModelScope.launch {
            delay(800) // Brief splash display
            val isLoggedIn = isLoggedInUseCase()
            val role = if (isLoggedIn) getUserRoleUseCase() else null
            // An unreadable preference must never trap the user in onboarding or hide the login screen.
            var onboarded = runCatching { preferences.onboardingComplete.first() }.getOrDefault(true)
            if (!onboarded && isLoggedIn) {
                // Accounts that were already signed in before this flag existed have, by definition, moved past onboarding.
                runCatching { preferences.setOnboardingComplete() }
                onboarded = true
            }
            _splashState.value = SplashState(isReady = true, isLoggedIn = isLoggedIn, userRole = role, onboardingComplete = onboarded)
        }
    }
}
