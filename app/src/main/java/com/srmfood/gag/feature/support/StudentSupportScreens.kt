package com.srmfood.gag.feature.support

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.core.ui.component.GagEmptyScreen
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.GagPrimaryButton
import com.srmfood.gag.core.ui.component.GagTextField
import com.srmfood.gag.core.ui.component.GagTopBar
import com.srmfood.gag.core.ui.theme.GagError
import com.srmfood.gag.domain.model.SupportMessageAuthor
import com.srmfood.gag.domain.model.SupportTicket
import com.srmfood.gag.domain.model.SupportTicketCategory
import com.srmfood.gag.domain.model.SupportTicketStatus

/** Short "2025-01-31T10:15:00+00:00" → "2025-01-31 10:15" (display only). */
internal fun shortTime(iso: String): String = iso.take(16).replace('T', ' ')

@Composable
internal fun StatusChip(status: SupportTicketStatus, management: Boolean = false) {
    val container = when (status) {
        SupportTicketStatus.WAITING_FOR_STUDENT -> MaterialTheme.colorScheme.tertiaryContainer
        SupportTicketStatus.RESOLVED, SupportTicketStatus.CLOSED -> MaterialTheme.colorScheme.surfaceVariant
        SupportTicketStatus.UNKNOWN -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    Surface(shape = RoundedCornerShape(20.dp), color = container) {
        Text(
            if (management) status.managementLabel() else status.label(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ─── Report a problem ─────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportProblemScreen(
    orderId: String,
    onBack: () -> Unit,
    onViewTicket: (String) -> Unit,
    viewModel: ReportProblemViewModel = hiltViewModel()
) {
    LaunchedEffect(orderId) { viewModel.load(orderId) }
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { GagTopBar(title = "Report a problem", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val created = state.createdTicket
        when {
            created != null -> Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Report sent", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your ticket ${created.ticketNumber} has been raised. Our team will review it and reply here.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(24.dp))
                GagPrimaryButton(text = "View ticket", onClick = { onViewTicket(created.id) }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = onBack) { Text("Back to order") }
            }
            state.order is UiState.Loading -> GagLoadingScreen(modifier = Modifier.padding(padding))
            state.order is UiState.Error -> GagErrorScreen(
                message = (state.order as UiState.Error).message,
                onRetry = { viewModel.load(orderId) },
                modifier = Modifier.padding(padding)
            )
            else -> {
                val order = (state.order as UiState.Success).data
                Column(
                    modifier = Modifier.padding(padding).fillMaxSize().imePadding()
                        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Order ${order.orderNumber} · ${order.outletName}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                    )
                    Text("What went wrong?", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SupportTicketCategory.selectable.forEach { c ->
                            FilterChip(
                                selected = state.category == c,
                                onClick = { viewModel.setCategory(c) },
                                enabled = !state.isSubmitting,
                                label = { Text(c.label()) }
                            )
                        }
                    }
                    if (state.category == SupportTicketCategory.FOOD_QUALITY || state.category == SupportTicketCategory.MISSING_ITEMS) {
                        Text("Which item? (optional)", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            order.items.distinctBy { it.foodItemId }.forEach { item ->
                                FilterChip(
                                    selected = state.foodItemId == item.foodItemId,
                                    onClick = { viewModel.setFoodItem(if (state.foodItemId == item.foodItemId) null else item.foodItemId) },
                                    enabled = !state.isSubmitting,
                                    label = { Text(item.foodName) }
                                )
                            }
                        }
                    }
                    GagTextField(
                        value = state.subject, onValueChange = viewModel::setSubject, label = "Subject",
                        isError = state.showValidation && state.errors.subject != null,
                        errorMessage = if (state.showValidation) state.errors.subject else null,
                        enabled = !state.isSubmitting,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth()
                    )
                    GagTextField(
                        value = state.description, onValueChange = viewModel::setDescription, label = "Describe the problem",
                        isError = state.showValidation && state.errors.description != null,
                        errorMessage = if (state.showValidation) state.errors.description else null,
                        enabled = !state.isSubmitting,
                        singleLine = false, maxLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.submitError?.let { msg ->
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Column(Modifier.padding(12.dp).fillMaxWidth()) {
                                Text(msg, color = GagError, style = MaterialTheme.typography.bodyMedium)
                                state.existingTicketId?.let { id ->
                                    TextButton(onClick = { onViewTicket(id) }) { Text("Open existing ticket") }
                                }
                            }
                        }
                    }
                    GagPrimaryButton(
                        text = "Submit report",
                        onClick = viewModel::submit,
                        isLoading = state.isSubmitting,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ─── My tickets ───────────────────────────────────────────────────────────────

@Composable
fun MyTicketsScreen(
    onBack: () -> Unit,
    onOpenTicket: (String) -> Unit,
    viewModel: MyTicketsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = { GagTopBar(title = "My support tickets", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (val s = state) {
            is UiState.Loading, is UiState.Idle -> GagLoadingScreen(modifier = Modifier.padding(padding))
            is UiState.Error -> GagErrorScreen(message = s.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is UiState.Empty -> GagEmptyScreen(
                title = "No tickets yet",
                message = "If something goes wrong with an order, use “Report a problem” on the order page.",
                icon = Icons.Outlined.ConfirmationNumber,
                modifier = Modifier.padding(padding)
            )
            is UiState.Success -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(s.data, key = { it.id }) { t -> TicketRow(t) { onOpenTicket(t.id) } }
            }
        }
    }
}

@Composable
private fun TicketRow(t: SupportTicket, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .semantics { contentDescription = "Ticket ${t.ticketNumber}, ${t.subject}, ${t.status.label()}" }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(t.ticketNumber, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                StatusChip(t.status)
            }
            Text(t.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("${t.category.label()} · ${shortTime(t.updatedAt)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Ticket detail ────────────────────────────────────────────────────────────

@Composable
fun StudentTicketScreen(
    ticketId: String,
    onBack: () -> Unit,
    viewModel: StudentTicketViewModel = hiltViewModel()
) {
    LaunchedEffect(ticketId) { viewModel.load(ticketId) }
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = { GagTopBar(title = "Ticket", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (val ts = state.ticket) {
            is UiState.Loading, is UiState.Idle, is UiState.Empty -> GagLoadingScreen(modifier = Modifier.padding(padding))
            is UiState.Error -> GagErrorScreen(message = ts.message, onRetry = { viewModel.load(ticketId) }, modifier = Modifier.padding(padding))
            is UiState.Success -> {
                val t = ts.data
                Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(t.ticketNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                StatusChip(t.status)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(t.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(t.category.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        item { StatusBanner(t) }
                        item { MessageBubble("You", t.description, t.createdAt, mine = true) }
                        items(state.messages, key = { it.id }) { m ->
                            val mine = m.authorRole == SupportMessageAuthor.STUDENT
                            MessageBubble(if (mine) "You" else "CRAVE support", m.body, m.createdAt, mine)
                        }
                    }
                    if (state.canReply) {
                        state.sendError?.let {
                            Text(it, color = GagError, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
                        }
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GagTextField(
                                value = state.reply, onValueChange = viewModel::setReply, label = "Reply",
                                singleLine = false, maxLines = 4, enabled = !state.isSending, modifier = Modifier.weight(1f)
                            )
                            Button(onClick = viewModel::send, enabled = !state.isSending && state.reply.isNotBlank()) {
                                if (state.isSending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Send")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(t: SupportTicket) {
    val (text, color) = when (t.status) {
        SupportTicketStatus.WAITING_FOR_STUDENT ->
            "Our team needs more information from you. Please reply below." to MaterialTheme.colorScheme.tertiaryContainer
        SupportTicketStatus.RESOLVED ->
            ("Resolved" + (t.resolutionReason?.let { ": $it" } ?: "")) to MaterialTheme.colorScheme.secondaryContainer
        SupportTicketStatus.CLOSED ->
            ("Closed" + (t.resolutionReason?.let { ": $it" } ?: "")) to MaterialTheme.colorScheme.surfaceVariant
        SupportTicketStatus.UNKNOWN ->
            "This ticket has a status this app version doesn't recognise. Please update the app." to MaterialTheme.colorScheme.errorContainer
        else -> return
    }
    Surface(shape = RoundedCornerShape(12.dp), color = color, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun MessageBubble(author: String, body: String, time: String, mine: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(author, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(shortTime(time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
