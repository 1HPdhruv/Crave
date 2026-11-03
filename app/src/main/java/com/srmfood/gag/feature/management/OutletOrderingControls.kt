package com.srmfood.gag.feature.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srmfood.gag.domain.model.Outlet
import com.srmfood.gag.domain.model.OutletManagementActionRecord
import com.srmfood.gag.domain.model.OutletOrderingAction
import com.srmfood.gag.domain.model.OutletOrderingStatus

fun outletActionErrorMessage(error: Throwable?): String {
    val raw = error?.message.orEmpty()
    return when {
        "Management access required" in raw -> "Only an active administrator can change an outlet's ordering status."
        "CONFIRMATION_REQUIRED" in raw -> "Type the outlet's exact name to confirm."
        "REASON_REQUIRED" in raw -> "Please give a clear reason (more detail is needed for suspension and deactivation)."
        "INVALID_TRANSITION" in raw -> "That action isn't allowed from the outlet's current state. It has been refreshed."
        "OUTLET_NOT_FOUND" in raw -> "This outlet could not be found."
        "Could not find the function" in raw || "PGRST202" in raw ->
            "This action isn't available on the server yet."
        else -> "Something went wrong. Please try again."
    }
}

fun OutletOrderingAction.label(): String = when (this) {
    OutletOrderingAction.FLAG -> "Flag for monitoring"
    OutletOrderingAction.SUSPEND -> "Suspend ordering"
    OutletOrderingAction.DEACTIVATE -> "Deactivate permanently"
    OutletOrderingAction.REACTIVATE -> "Reactivate ordering"
}

fun OutletOrderingStatus.managementLabel(): String = when (this) {
    OutletOrderingStatus.ACTIVE -> "Accepting orders"
    OutletOrderingStatus.FLAGGED -> "Flagged (monitoring)"
    OutletOrderingStatus.SUSPENDED -> "Ordering suspended"
    OutletOrderingStatus.DEACTIVATED -> "Permanently deactivated"
    OutletOrderingStatus.UNKNOWN -> "Unrecognised status"
}

private fun OutletOrderingAction.explanation(): String = when (this) {
    OutletOrderingAction.FLAG -> "Marks the outlet for internal monitoring. Students are not told and can still order."
    OutletOrderingAction.SUSPEND -> "Students can't place new orders. Existing orders, payments and pickups continue normally."
    OutletOrderingAction.DEACTIVATE -> "Permanent. New orders are blocked. Existing orders are preserved. Reactivation needs the outlet name."
    OutletOrderingAction.REACTIVATE -> "Lets students order again."
}

/** Management card: ordering state, separate from opening hours (is_open) and is_active. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutletOrderingControls(
    outlet: Outlet,
    state: OutletOrderingUiState,
    history: List<OutletManagementActionRecord>,
    onStart: (OutletOrderingAction) -> Unit,
    onCancel: () -> Unit,
    onReasonChange: (String) -> Unit,
    onConfirmNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismissError: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ordering status: ${outlet.orderingStatus.managementLabel()}", fontWeight = FontWeight.Bold)
        Text(
            "Separate from opening hours (open/closed) and from the account's active flag. A complaint never changes this automatically.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.error?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.padding(10.dp).fillMaxWidth()) {
                    Text(it, Modifier.weight(1f))
                    TextButton(onClick = onDismissError) { Text("Dismiss") }
                }
            }
        }
        val available = OutletOrderingAction.availableFrom(outlet.orderingStatus)
        if (available.isEmpty()) Text("No actions available for this state.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            available.forEach { a -> OutlinedButton(onClick = { onStart(a) }, enabled = !state.isBusy) { Text(a.label()) } }
        }
        if (history.isNotEmpty()) {
            Text("History", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            history.take(5).forEach { h ->
                Text("${h.createdAt.take(16).replace('T', ' ')} · ${h.action}: ${h.fromStatus} → ${h.toStatus}\n“${h.reason}”",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    state.pending?.let { action ->
        val needsName = action.requiresNameConfirmation(outlet.orderingStatus)
        val reasonOk = state.reason.trim().length >= action.minReasonLength
        val nameOk = !needsName || state.confirmName.trim() == outlet.name
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(action.label()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(action.explanation(), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = state.reason, onValueChange = onReasonChange, label = { Text("Reason (recorded in the audit trail)") },
                        minLines = 2, isError = state.reason.isNotEmpty() && !reasonOk,
                        supportingText = { Text("At least ${action.minReasonLength} characters") }
                    )
                    if (needsName) OutlinedTextField(
                        value = state.confirmName, onValueChange = onConfirmNameChange,
                        label = { Text("Type “${outlet.name}” to confirm") }, singleLine = true
                    )
                }
            },
            confirmButton = { Button(onClick = onSubmit, enabled = reasonOk && nameOk && !state.isBusy) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
        )
    }
}
