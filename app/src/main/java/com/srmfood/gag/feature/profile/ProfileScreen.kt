package com.srmfood.gag.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.srmfood.gag.core.ui.component.GagBottomNavBar
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.component.studentBottomNavItems
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
    onNavigateBottom: (String) -> Unit,
    onLogoutSuccess: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { GagTopBar(title = "Profile", onBack = null) },
        bottomBar = { GagBottomNavBar(items = studentBottomNavItems, currentRoute = "profile", onItemSelected = onNavigateBottom) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Profile Header
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(96.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = user?.name ?: "Loading...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = user?.email ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!user?.phone.isNullOrBlank()) {
                            Text(
                                text = user?.phone ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!user?.registrationNumber.isNullOrBlank()) {
                            Text(
                                text = user?.registrationNumber ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedButton(
                            onClick = { showEditDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Profile")
                        }
                    }
                }
            }

            // Quick Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    QuickActionCard(
                        title = "My Orders",
                        icon = Icons.Outlined.Edit, // TODO use List/Assignment
                        onClick = onNavigateToOrders,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        title = "Favorites",
                        icon = Icons.Outlined.FavoriteBorder,
                        onClick = onNavigateToFavorites,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Menu Options
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ProfileMenuItem(
                            icon = Icons.Outlined.Settings,
                            title = "Settings",
                            onClick = onNavigateToSettings
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileMenuItem(
                            icon = Icons.Outlined.HelpOutline,
                            title = "Help & Support",
                            onClick = onNavigateToHelp
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .clickable { viewModel.logout(); onLogoutSuccess() }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
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
                    enabled = updateState !is UpdateState.Loading && editName.isNotBlank()
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
private fun QuickActionCard(title: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProfileMenuItem(icon: ImageVector, title: String, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint)
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
