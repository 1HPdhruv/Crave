package com.srmfood.gag.feature.auth.register

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagPasswordField
import com.srmfood.gag.core.ui.component.GagPrimaryButton
import com.srmfood.gag.core.ui.component.GagTextField
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.*

import com.srmfood.gag.domain.repository.AuthResult

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val registerState by viewModel.registerState.collectAsState()
    val focusManager = LocalFocusManager.current
    var showVerificationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(registerState) {
        if (registerState is UiState.Success) {
            val result = (registerState as UiState.Success).data
            viewModel.resetState()
            if (result is AuthResult.EmailConfirmationRequired) {
                showVerificationDialog = true
            } else {
                onRegisterSuccess()
            }
        }
    }

    if (showVerificationDialog) {
        AlertDialog(
            onDismissRequest = { /* Force user to click login */ },
            title = { Text("Verify Your Email") },
            text = { Text("We've sent a confirmation link to your email address. Please verify your email before logging in.") },
            confirmButton = {
                TextButton(onClick = {
                    showVerificationDialog = false
                    onNavigateToLogin()
                }) {
                    Text("Go to Login")
                }
            }
        )
    }

    Scaffold(
        topBar = { GagTopBar(title = "Create Account", onBack = onNavigateToLogin) },
        containerColor = GagBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Join GaG at SRM KTR",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Create your student account to start ordering",
                style = MaterialTheme.typography.bodyMedium,
                color = GagOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            GagTextField(value = formState.name, onValueChange = viewModel::onNameChanged, label = "Full Name",
                leadingIcon = Icons.Outlined.Person, isError = formState.nameError != null, errorMessage = formState.nameError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }))

            GagTextField(value = formState.email, onValueChange = viewModel::onEmailChanged, label = "Email",
                leadingIcon = Icons.Outlined.Email, isError = formState.emailError != null, errorMessage = formState.emailError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }))

            GagTextField(value = formState.phone ?: "", onValueChange = viewModel::onPhoneChanged, label = "Phone (Optional)",
                leadingIcon = Icons.Outlined.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }))

            GagTextField(value = formState.registrationNumber ?: "", onValueChange = viewModel::onRegNoChanged,
                label = "SRM Registration Number (Optional)", leadingIcon = Icons.Outlined.Badge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }))

            GagPasswordField(value = formState.password, onValueChange = viewModel::onPasswordChanged, label = "Password",
                leadingIcon = Icons.Outlined.Lock, isError = formState.passwordError != null, errorMessage = formState.passwordError,
                imeAction = ImeAction.Done, keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); viewModel.register() }))

            if (registerState is UiState.Error) {
                Text(text = (registerState as UiState.Error).message, color = GagError, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))
            GagPrimaryButton(text = "Create Account", onClick = viewModel::register, isLoading = registerState is UiState.Loading)

            Row(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Already have an account? ", color = GagOnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text("Sign In", color = GagOrange, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(onClick = onNavigateToLogin))
            }
        }
    }
}
