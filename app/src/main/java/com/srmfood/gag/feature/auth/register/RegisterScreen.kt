package com.srmfood.gag.feature.auth.register

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
    var registeredAsVendor by remember { mutableStateOf(false) }

    LaunchedEffect(registerState) {
        if (registerState is UiState.Success) {
            val result = (registerState as UiState.Success).data
            registeredAsVendor = formState.selectedRole == RegistrationRole.VENDOR
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
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = {
                Text(
                    text = if (registeredAsVendor) "Vendor Application Submitted" else "Verify Your Email",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (registeredAsVendor) {
                        "We've sent a confirmation link to your email address.\n\n" +
                        "Your vendor account will be available after email verification and admin approval."
                    } else {
                        "We've sent a confirmation link to your email address. Please verify your email before logging in."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showVerificationDialog = false
                    onNavigateToLogin()
                }) {
                    Text("Go to Login", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    Scaffold(
        topBar = { GagTopBar(title = "Create Account", onBack = onNavigateToLogin) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ──────────────────────────────────────────────
            Text(
                text = "Join GaG at SRM KTR",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Create your account to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Role Selector ────────────────────────────────────────
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Register as",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoleCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Badge,
                    title = "Student",
                    description = "Order food and manage your orders",
                    isSelected = formState.selectedRole == RegistrationRole.STUDENT,
                    onClick = { viewModel.onRoleSelected(RegistrationRole.STUDENT) },
                    contentDesc = "Register as Student"
                )
                RoleCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Store,
                    title = "Vendor",
                    description = "Manage outlets, menus and orders",
                    isSelected = formState.selectedRole == RegistrationRole.VENDOR,
                    onClick = { viewModel.onRoleSelected(RegistrationRole.VENDOR) },
                    contentDesc = "Register as Vendor"
                )
            }

            // Vendor pending notice
            if (formState.selectedRole == RegistrationRole.VENDOR) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Text(
                            text = "Vendor accounts require admin approval after email verification. Your account will be reviewed before gaining vendor access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // ── Form Fields ──────────────────────────────────────────
            GagTextField(
                value = formState.name,
                onValueChange = viewModel::onNameChanged,
                label = "Full Name",
                leadingIcon = Icons.Outlined.Person,
                isError = formState.nameError != null,
                errorMessage = formState.nameError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )

            GagTextField(
                value = formState.email,
                onValueChange = viewModel::onEmailChanged,
                label = "Email",
                leadingIcon = Icons.Outlined.Email,
                isError = formState.emailError != null,
                errorMessage = formState.emailError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )

            GagTextField(
                value = formState.phone ?: "",
                onValueChange = viewModel::onPhoneChanged,
                label = "Phone (Optional)",
                leadingIcon = Icons.Outlined.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )

            if (formState.selectedRole == RegistrationRole.STUDENT) {
                GagTextField(
                    value = formState.registrationNumber ?: "",
                    onValueChange = viewModel::onRegNoChanged,
                    label = "SRM Registration Number (Optional)",
                    leadingIcon = Icons.Outlined.Badge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )
            }

            GagPasswordField(
                value = formState.password,
                onValueChange = viewModel::onPasswordChanged,
                label = "Password",
                leadingIcon = Icons.Outlined.Lock,
                isError = formState.passwordError != null,
                errorMessage = formState.passwordError,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    viewModel.register()
                })
            )

            if (registerState is UiState.Error) {
                Text(
                    text = (registerState as UiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            GagPrimaryButton(
                text = when (formState.selectedRole) {
                    RegistrationRole.VENDOR -> "Submit Vendor Application"
                    RegistrationRole.STUDENT -> "Create Account"
                },
                onClick = viewModel::register,
                isLoading = registerState is UiState.Loading
            )

            Row(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    "Already have an account? ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Sign In",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(onClick = onNavigateToLogin)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RoleCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    contentDesc: String
) {
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "roleBorderColor"
    )
    val animatedContainerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "roleContainerColor"
    )
    val animatedContentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "roleContentColor"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .semantics {
                role = Role.RadioButton
                contentDescription = contentDesc
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = animatedContainerColor,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = animatedBorderColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = animatedContentColor,
                    modifier = Modifier.size(24.dp)
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(2.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = animatedContentColor
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = animatedContentColor.copy(alpha = 0.8f),
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight
            )
        }
    }
}
