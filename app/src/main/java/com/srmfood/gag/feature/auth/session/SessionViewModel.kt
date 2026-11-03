package com.srmfood.gag.feature.auth.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.domain.usecase.auth.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity-level session actions shared by every role's shell. Logging out ends the real session first
 * (push token detached, Supabase sign-out, per-user caches cleared) and only then navigates, so the next
 * app start cannot silently restore the previous account.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(private val logoutUseCase: LogoutUseCase) : ViewModel() {
    private var inFlight = false

    fun logout(onDone: () -> Unit) {
        if (inFlight) return
        inFlight = true
        viewModelScope.launch {
            try { runCatching { logoutUseCase() } } finally { inFlight = false }
            onDone()
        }
    }
}
