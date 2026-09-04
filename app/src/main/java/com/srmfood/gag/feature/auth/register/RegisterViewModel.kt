package com.srmfood.gag.feature.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.repository.AuthResult
import com.srmfood.gag.domain.usecase.auth.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterFormState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val phone: String? = null,
    val registrationNumber: String? = null,
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase
) : ViewModel() {

    private val _formState = MutableStateFlow(RegisterFormState())
    val formState: StateFlow<RegisterFormState> = _formState.asStateFlow()

    private val _registerState = MutableStateFlow<UiState<AuthResult>>(UiState.Idle)
    val registerState: StateFlow<UiState<AuthResult>> = _registerState.asStateFlow()

    fun onNameChanged(name: String) = _formState.update { it.copy(name = name, nameError = null) }
    fun onEmailChanged(email: String) = _formState.update { it.copy(email = email, emailError = null) }
    fun onPasswordChanged(password: String) = _formState.update { it.copy(password = password, passwordError = null) }
    fun onPhoneChanged(phone: String) = _formState.update { it.copy(phone = phone.ifBlank { null }) }
    fun onRegNoChanged(regNo: String) = _formState.update { it.copy(registrationNumber = regNo.ifBlank { null }) }

    fun register() {
        val form = _formState.value
        var hasError = false
        if (form.name.isBlank()) { _formState.update { it.copy(nameError = "Name is required") }; hasError = true }
        if (form.email.isBlank()) { _formState.update { it.copy(emailError = "Email is required") }; hasError = true }
        if (form.password.length < 8) { _formState.update { it.copy(passwordError = "Password must be at least 8 characters") }; hasError = true }
        if (hasError) return

        viewModelScope.launch {
            _registerState.value = UiState.Loading
            val result = registerUseCase(form.name, form.email, form.password, form.phone, form.registrationNumber)
            _registerState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Registration failed") }
            )
        }
    }

    fun resetState() { _registerState.value = UiState.Idle }
}
