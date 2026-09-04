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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SplashState(
    val isReady: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userRole: UserRole? = null,
    val onboardingComplete: Boolean = true  // TODO: persist via DataStore
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val isLoggedInUseCase: IsLoggedInUseCase,
    private val getUserRoleUseCase: GetUserRoleUseCase
) : ViewModel() {

    private val _splashState = MutableStateFlow(SplashState())
    val splashState: StateFlow<SplashState> = _splashState.asStateFlow()

    init {
        viewModelScope.launch {
            delay(800) // Brief splash display
            val isLoggedIn = isLoggedInUseCase()
            val role = if (isLoggedIn) getUserRoleUseCase() else null
            _splashState.value = SplashState(
                isReady = true,
                isLoggedIn = isLoggedIn,
                userRole = role,
                onboardingComplete = true  // TODO: read from DataStore
            )
        }
    }
}
