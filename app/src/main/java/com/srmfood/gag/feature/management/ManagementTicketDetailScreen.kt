package com.srmfood.gag.feature.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.domain.model.SupportMessageAuthor
import com.srmfood.gag.domain.model.SupportTicketDetail
import com.srmfood.gag.domain.model.SupportTicketPriority
import com.srmfood.gag.domain.model.SupportTicketStatus
import com.srmfood.gag.feature.support.StatusChip
import com.srmfood.gag.feature.support.label
import com.srmfood.gag.feature.support.managementLabel
import com.srmfood.gag.feature.support.shortTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManagementTicketDetailScreen(
    ticketId: String,
    onNavigateBack: () -> Unit,
    onOpenOrder: (String) -> Unit,
    viewModel: ManagementTicketDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(ticketId) { viewModel.load(ticketId) }
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ticket", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Outlined.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        when (val d = state.detail) {
            is UiState.Success -> {
                val detail = d.data
                val t = detail.ticket
                LazyColumn(
                    Modifier.padding(padding).fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    state.actionError?.let { err ->
                        item {
                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(err, Modifier.weight(1f))
                                    TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(t.ticketNumber, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            StatusChip(t.status, management = true)
                        }
                        Text(t.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("${t.category.label()} · priority ${t.priority.label()} · opened ${shortTime(t.createdAt)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item { LinkedInfo(detail, onOpenOrder) }
                    item { Section("Student's report") { Text(t.description) } }
                    item { AssignmentAndPriority(detail, state.isBusy, state.assignees, viewModel) }
                    state.linkedReviews?.let { r -> item { LinkedReviews(r) } }
                    item { StatusActions(t.status, state.isBusy, viewModel::requestTransition) }
                    t.resolutionReason?.let { r -> item { Section("Resolution reason") { Text(r) } } }

                    item { Text("Conversation with student", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                    if (detail.messages.isEmpty()) item { Text("No messages yet.", style = MaterialTheme.typography.bodySmall) }
                    items(detail.messages, key = { it.id }) { m ->
                        val student = m.authorRole == SupportMessageAuthor.STUDENT
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (student) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(Modifier.padding(12.dp).fillMaxWidth()) {
                                Text(if (student) "Student" else "Management", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(m.body)
                                Text(shortTime(m.createdAt), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    item {
                        val canRespond = t.status != SupportTicketStatus.CLOSED && t.status != SupportTicketStatus.UNKNOWN
                        if (canRespond) {
                            OutlinedTextField(
                                value = state.responseDraft, onValueChange = viewModel::setResponseDraft,
                                label = { Text("Reply to student (visible to student)") }, minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(onClick = viewModel::sendResponse, enabled = !state.isBusy && state.responseDraft.isNotBlank()) {
                                Text("Send reply")
                            }
                        }
                    }

                    item { HorizontalDivider() }
                    item {
                        Text("Internal notes — confidential, never shown to students or vendors",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary)
                    }
                    items(detail.internalNotes, key = { it.id }) { n ->
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Column(Modifier.padding(12.dp).fillMaxWidth()) {
                                Text("${n.authorName ?: "Admin"} · ${shortTime(n.createdAt)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(n.note)
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = state.noteDraft, onValueChange = viewModel::setNoteDraft,
                            label = { Text("Add internal note") }, minLines = 2, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(onClick = viewModel::addNote, enabled = !state.isBusy && state.noteDraft.isNotBlank()) { Text("Save note") }
                    }

                    item { HorizontalDivider() }
                    item { Text("Audit timeline", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                    items(detail.events, key = { it.id }) { e ->
                        Column {
                            Text("${e.action.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}" +
                                (if (e.fromValue != null || e.toValue != null) ": ${e.fromValue ?: "—"} → ${e.toValue ?: "—"}" else ""),
                                style = MaterialTheme.typography.bodyMedium)
                            Text("${e.actorName ?: e.actorRole} · ${shortTime(e.createdAt)}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            e.reason?.let { Text("Reason: $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }

                state.pendingTransition?.let { target ->
                    AlertDialog(
                        onDismissRequest = viewModel::cancelTransition,
                        title = { Text("Move to ${target.managementLabel()}") },
                        text = {
                            Column {
                                Text(
                                    if (target == SupportTicketStatus.CLOSED) "Closed tickets are final and cannot be reopened."
                                    else "A reason is required and is recorded in the audit trail.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = state.reasonDraft, onValueChange = viewModel::setReasonDraft,
                                    label = { Text("Reason") }, isError = state.reasonError != null,
                                    supportingText = state.reasonError?.let { e -> { Text(e) } }, minLines = 2
                                )
                            }
                        },
                        confirmButton = { Button(onClick = viewModel::confirmTransition, enabled = !state.isBusy) { Text("Confirm") } },
                        dismissButton = { TextButton(onClick = viewModel::cancelTransition) { Text("Cancel") } }
                    )
                }
            }
            is UiState.Error -> GagErrorScreen(message = d.message, onRetry = { viewModel.load(ticketId) }, modifier = Modifier.padding(padding))
            else -> GagLoadingScreen(modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun LinkedInfo(d: SupportTicketDetail, onOpenOrder: (String) -> Unit) {
    Section("Linked records") {
        Text("Student: ${d.studentName}${if (d.studentEmail.isNotBlank()) " (${d.studentEmail})" else ""}")
        Text("Outlet: ${d.outletName ?: "—"}" + when (d.outletIsActive) { false -> " (inactive)"; else -> "" })
        val orderId = d.ticket.orderId
        if (orderId != null) {
            Text("Order: ${d.orderNumber ?: orderId}${d.orderStatus?.let { " · $it" } ?: ""}")
            TextButton(onClick = { onOpenOrder(orderId) }) { Text("Open order") }
        } else Text("Order: not linked")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssignmentAndPriority(
    d: SupportTicketDetail, busy: Boolean,
    assignees: List<com.srmfood.gag.domain.model.SupportAssignee>?, vm: ManagementTicketDetailViewModel
) {
    val closed = d.ticket.status == SupportTicketStatus.CLOSED
    val me = vm.currentUserId
    Section("Assignment & priority") {
        Text("Assigned to: ${d.assigneeName ?: "Nobody"}")
        if (!assignees.isNullOrEmpty()) {
            Text("Assign to", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                assignees.forEach { a ->
                    FilterChip(selected = d.assignedTo == a.id, onClick = { vm.assignTo(a.id) },
                        enabled = !busy && !closed && d.assignedTo != a.id, label = { Text(if (a.id == me) "${a.name} (me)" else a.name) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (assignees.isNullOrEmpty() && d.assignedTo != me && me != null) OutlinedButton(onClick = vm::assignToMe, enabled = !busy && !closed) { Text("Assign to me") }
            if (d.assignedTo != null) OutlinedButton(onClick = vm::unassign, enabled = !busy && !closed) { Text("Unassign") }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SupportTicketPriority.selectable.forEach { p ->
                FilterChip(selected = d.ticket.priority == p, onClick = { vm.setPriority(p) },
                    enabled = !busy && !closed, label = { Text(p.label()) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusActions(status: SupportTicketStatus, busy: Boolean, onTransition: (SupportTicketStatus) -> Unit) {
    Section("Change status") {
        if (status.allowedTransitions.isEmpty()) {
            Text(if (status == SupportTicketStatus.UNKNOWN) "Unrecognised status — update the app to manage this ticket." else "Closed tickets are final.")
        } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            status.allowedTransitions.forEach { target ->
                val label = if (status == SupportTicketStatus.RESOLVED && target == SupportTicketStatus.UNDER_REVIEW) "Reopen" else target.managementLabel()
                OutlinedButton(onClick = { onTransition(target) }, enabled = !busy) { Text(label) }
            }
        }
    }
}

@Composable
private fun LinkedReviews(r: com.srmfood.gag.domain.model.SupportTicketReviews) {
    Section("Reviews on this order") {
        if (r.foodReviews.isEmpty() && r.outletReview == null) Text("The student left no reviews for this order.")
        r.foodReviews.forEach { f ->
            Text("${f.foodName}: ${f.rating}/5${if (!f.isVisible) " (hidden)" else ""}", fontWeight = FontWeight.SemiBold)
            f.text?.takeIf { it.isNotBlank() }?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall) }
        }
        r.outletReview?.let { o ->
            Spacer(Modifier.height(4.dp))
            Text("Outlet: ${o.rating}/5${if (!o.isVisible) " (hidden)" else ""}", fontWeight = FontWeight.SemiBold)
            o.text?.takeIf { it.isNotBlank() }?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
