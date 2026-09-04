package com.srmfood.gag.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.User
import com.srmfood.gag.domain.model.UserRole
import com.srmfood.gag.domain.usecase.auth.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginFormState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isPasswordVisible: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    private val _formState = MutableStateFlow(LoginFormState())
    val formState: StateFlow<LoginFormState> = _formState.asStateFlow()

    private val _loginState = MutableStateFlow<UiState<User>>(UiState.Idle)
    val loginState: StateFlow<UiState<User>> = _loginState.asStateFlow()

    fun onEmailChanged(email: String) {
        _formState.update { it.copy(email = email, emailError = null) }
    }

    fun onPasswordChanged(password: String) {
        _formState.update { it.copy(password = password, passwordError = null) }
    }

    fun togglePasswordVisibility() {
        _formState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun login() {
        val form = _formState.value
        var hasError = false

        if (form.email.isBlank()) {
            _formState.update { it.copy(emailError = "Email is required") }
            hasError = true
        }
        if (form.password.isBlank()) {
            _formState.update { it.copy(passwordError = "Password is required") }
            hasError = true
        }
        if (hasError) return

        viewModelScope.launch {
            _loginState.value = UiState.Loading
            val result = loginUseCase(form.email, form.password)
            _loginState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Login failed") }
            )
        }
    }

    fun resetState() {
        _loginState.value = UiState.Idle
    }
}
