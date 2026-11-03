package com.srmfood.gag.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.ui.component.*
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.model.User
import com.srmfood.gag.domain.usecase.auth.GetCurrentUserUseCase
import com.srmfood.gag.domain.usecase.auth.LogoutUseCase
import com.srmfood.gag.domain.usecase.auth.UpdateProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    init {
        viewModelScope.launch {
            getCurrentUserUseCase().collectLatest { _user.value = it }
        }
    }

    fun updateProfile(name: String, phone: String?, registrationNumber: String?) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Loading
            updateProfileUseCase(name, phone, registrationNumber)
                .onSuccess {
                    _updateState.value = UpdateState.Success
                }
                .onFailure {
                    _updateState.value = UpdateState.Error(it.message ?: "Update failed")
                }
        }
    }

    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
    }

    fun logout() { viewModelScope.launch { logoutUseCase() } }
}

sealed class UpdateState {
    object Idle : UpdateState()
    object Loading : UpdateState()
    object Success : UpdateState()
    data class Error(val message: String) : UpdateState()
}

@Composable
fun ProfileScreen(
    onLogoutSuccess: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToTickets: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp).statusBarsPadding()) }

            // Profile Header — website `.profile-hero` (lavender brutal card)
            item {
                val roleLabel = when (user?.role) {
                    com.srmfood.gag.domain.model.UserRole.ADMIN -> "Management"
                    com.srmfood.gag.domain.model.UserRole.VENDOR -> "Vendor"
                    com.srmfood.gag.domain.model.UserRole.PENDING_VENDOR -> "Vendor (pending)"
                    else -> "Student"
                }
                val initials = (user?.name ?: "").trim().split(" ").filter { it.isNotBlank() }
                    .take(2).joinToString("") { it.first().uppercase() }.ifBlank { "C" }
                Column(modifier = Modifier.padding(horizontal = 17.dp)) {
                    CraveEyebrow("Your profile")
                    Spacer(Modifier.height(10.dp))
                    CraveBrutalCard(modifier = Modifier.fillMaxWidth(), container = CraveLavender) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(CraveLavender)
                                        .border(2.dp, CraveInk, RoundedCornerShape(22.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(initials, style = MaterialTheme.typography.headlineSmall, color = CraveInk)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    CraveStateBadge(roleLabel, container = CraveChartreuse)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = user?.name ?: "Loading...",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = CraveInk,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = user?.email ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF514A70),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (!user?.phone.isNullOrBlank() || !user?.registrationNumber.isNullOrBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = listOfNotNull(user?.phone?.takeIf { it.isNotBlank() }, user?.registrationNumber?.takeIf { it.isNotBlank() }).joinToString("  ·  "),
                                    style = GagMonoStyle.copy(fontSize = 11.sp),
                                    color = Color(0xFF514A70)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CraveInk)
                                    .clickable { showEditDialog = true }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit profile", modifier = Modifier.size(16.dp), tint = CravePaper)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Edit profile", color = CravePaper, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Quick Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionCard(
                        title = "My orders",
                        icon = Icons.Default.ShoppingBag,
                        tint = CraveInk,
                        onClick = onNavigateToOrders,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        title = "Saved bites",
                        icon = Icons.Outlined.FavoriteBorder,
                        tint = CraveInk,
                        onClick = onNavigateToFavorites,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Menu Options
            item {
                Surface(
                    shape = RoundedCornerShape(17.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp)
                ) {
                    Column {
                        ProfileMenuItem(
                            icon = Icons.Outlined.Settings,
                            title = "Settings",
                            subtitle = "Theme, notifications",
                            onClick = onNavigateToSettings
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        ProfileMenuItem(
                            icon = Icons.Filled.Notifications,
                            title = "Notifications",
                            subtitle = "Order updates & alerts",
                            onClick = onNavigateToNotifications
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        ProfileMenuItem(
                            icon = Icons.Outlined.HelpOutline,
                            title = "Help & Support",
                            subtitle = "FAQs, contact us",
                            onClick = onNavigateToHelp
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        ProfileMenuItem(
                            icon = Icons.Outlined.HelpOutline,
                            title = "My support tickets",
                            subtitle = "Track problems you've reported",
                            onClick = onNavigateToTickets
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CraveRedSoft,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 17.dp, vertical = 8.dp)
                        .clickable { showLogoutDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.ExitToApp, contentDescription = "Log out", tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Log Out",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Log Out?", fontWeight = FontWeight.ExtraBold) },
            text = { Text("Are you sure you want to log out of Crave?") },
            confirmButton = {
                Button(
                    onClick = { showLogoutDialog = false; viewModel.logout(); onLogoutSuccess() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Log Out", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showEditDialog && user != null) {
        var editName by remember { mutableStateOf(user!!.name) }
        var editPhone by remember { mutableStateOf(user!!.phone ?: "") }
        var editReg by remember { mutableStateOf(user!!.registrationNumber ?: "") }

        AlertDialog(
            onDismissRequest = { 
                if (updateState !is UpdateState.Loading) {
                    showEditDialog = false
                    viewModel.resetUpdateState()
                }
            },
            title = { Text("Edit Profile", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = editReg,
                        onValueChange = { editReg = it },
                        label = { Text("Registration Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    when (updateState) {
                        is UpdateState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        is UpdateState.Error -> Text((updateState as UpdateState.Error).message, color = MaterialTheme.colorScheme.error)
                        is UpdateState.Success -> {
                            LaunchedEffect(Unit) {
                                showEditDialog = false
                                viewModel.resetUpdateState()
                            }
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.updateProfile(editName, editPhone.ifBlank { null }, editReg.ifBlank { null }) },
                    enabled = updateState !is UpdateState.Loading && editName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CraveOrange, contentColor = CraveInk),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CraveInk),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showEditDialog = false
                        viewModel.resetUpdateState()
                    },
                    enabled = updateState !is UpdateState.Loading
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CravePanel(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(13.dp)) {
        Column(modifier = Modifier.padding(17.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CraveChartreuse)
                    .border(1.dp, CraveInk, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(CraveWhite)
                .border(1.dp, CraveInk, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = tint)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
