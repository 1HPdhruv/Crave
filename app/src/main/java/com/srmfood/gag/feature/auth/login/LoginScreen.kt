package com.srmfood.gag.feature.auth.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagPasswordField
import com.srmfood.gag.core.ui.component.GagPrimaryButton
import com.srmfood.gag.core.ui.component.GagTextField
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.UserRole

@Composable
fun LoginScreen(
    onLoginSuccess: (UserRole) -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val loginState by viewModel.loginState.collectAsState()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // Handle login success
    LaunchedEffect(loginState) {
        if (loginState is UiState.Success) {
            val user = (loginState as UiState.Success).data
            viewModel.resetState()
            onLoginSuccess(user.role)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GagBackground)
    ) {
        // Gradient overlay top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GagOrangeDark.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Logo
            Text(
                text = "GaG",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = GagOrange
            )
            Text(
                text = "Grab & Go",
                style = MaterialTheme.typography.bodyMedium,
                color = GagOnSurfaceVariant
            )
            Text(
                text = "SRM KTR Campus Food",
                style = MaterialTheme.typography.bodySmall,
                color = GagOnSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Login Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = GagSurface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Welcome back",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sign in to your account",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GagOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    GagTextField(
                        value = formState.email,
                        onValueChange = viewModel::onEmailChanged,
                        label = "Email",
                        leadingIcon = Icons.Outlined.Email,
                        isError = formState.emailError != null,
                        errorMessage = formState.emailError,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    GagPasswordField(
                        value = formState.password,
                        onValueChange = viewModel::onPasswordChanged,
                        label = "Password",
                        leadingIcon = Icons.Outlined.Lock,
                        isError = formState.passwordError != null,
                        errorMessage = formState.passwordError,
                        imeAction = ImeAction.Done,
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus(); viewModel.login() }
                        )
                    )

                    // Error banner
                    AnimatedVisibility(
                        visible = loginState is UiState.Error,
                        enter = fadeIn(), exit = fadeOut()
                    ) {
                        val errMsg = (loginState as? UiState.Error)?.message ?: ""
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GagErrorContainer,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) {
                            Text(
                                text = errMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = GagError,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {},
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Forgot Password?", color = GagOrange, style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    GagPrimaryButton(
                        text = "Sign In",
                        onClick = viewModel::login,
                        isLoading = loginState is UiState.Loading
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Register
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Don't have an account? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GagOnSurfaceVariant
                )
                Text(
                    text = "Register",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = GagOrange,
                    modifier = Modifier.clickable(onClick = onNavigateToRegister)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Mock credentials hint
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GagSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Mock Credentials (Demo)", style = MaterialTheme.typography.labelMedium, color = GagAmber, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Student: student@srmist.edu.in / student123", style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                    Text("Vendor:  vendor@srm.ac.in / vendor123", style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                    Text("Admin:   admin@srm.ac.in / admin123", style = MaterialTheme.typography.bodySmall, color = GagOnSurfaceVariant)
                }
            }
        }
    }
}
