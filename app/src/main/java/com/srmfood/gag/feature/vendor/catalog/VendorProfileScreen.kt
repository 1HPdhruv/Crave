package com.srmfood.gag.feature.vendor.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.GagError
import com.srmfood.gag.domain.model.UserRole
import com.srmfood.gag.feature.profile.ProfileViewModel
import com.srmfood.gag.feature.profile.UpdateState

/**
 * Vendor profile. Reuses the student ProfileViewModel (same authenticated-user flow, same updateProfile use case),
 * so it always shows the signed-in account; if that account isn't a vendor it shows nothing of it.
 */
@Composable
fun VendorProfileScreen(onBack: () -> Unit, onLogout: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val user by viewModel.user.collectAsState()
    val update by viewModel.updateState.collectAsState()
    val u = user
    var name by remember(u?.id) { mutableStateOf(u?.name.orEmpty()) }
    var phone by remember(u?.id) { mutableStateOf(u?.phone.orEmpty()) }
    val nameOk = name.trim().length in 2..60
    val phoneOk = VendorRulesPhone.ok(phone)

    Scaffold(topBar = { GagTopBar(title = "Profile", onBack = onBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        when {
            u == null -> GagLoadingScreen(modifier = Modifier.padding(padding))
            u.role != UserRole.VENDOR -> Column(Modifier.padding(padding).padding(24.dp)) { Text("This screen is for vendor accounts.") }
            else -> Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(u.email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Vendor account · email can't be changed here", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { name = it.take(60); viewModel.resetUpdateState() }, label = { Text("Name") }, singleLine = true, isError = !nameOk, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it.take(20); viewModel.resetUpdateState() }, label = { Text("Phone") }, singleLine = true, isError = !phoneOk, modifier = Modifier.fillMaxWidth())
                when (val s = update) {
                    is UpdateState.Error -> Text(s.message, color = GagError)
                    UpdateState.Success -> Text("Profile saved")
                    else -> {}
                }
                Button(onClick = { viewModel.updateProfile(name.trim(), phone.trim().ifEmpty { null }, u.registrationNumber) },
                    enabled = nameOk && phoneOk && update !is UpdateState.Loading, modifier = Modifier.fillMaxWidth()) { Text(if (update is UpdateState.Loading) "Saving…" else "Save") }
                HorizontalDivider()
                OutlinedButton(onClick = { onLogout() }, modifier = Modifier.fillMaxWidth()) { Text("Log out", color = GagError) }
            }
        }
    }
}

private object VendorRulesPhone {
    fun ok(p: String) = com.srmfood.gag.domain.model.vendor.VendorRules.validatePhone(p) == null
}
