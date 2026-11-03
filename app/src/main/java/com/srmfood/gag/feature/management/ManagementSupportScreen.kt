package com.srmfood.gag.feature.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.ui.component.GagEmptyScreen
import com.srmfood.gag.core.ui.component.GagErrorScreen
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.domain.model.SupportTicketCategory
import com.srmfood.gag.domain.model.SupportTicketPriority
import com.srmfood.gag.domain.model.SupportTicketStatus
import com.srmfood.gag.domain.model.SupportTicketSummary
import com.srmfood.gag.feature.support.StatusChip
import com.srmfood.gag.feature.support.label
import com.srmfood.gag.feature.support.managementLabel
import com.srmfood.gag.feature.support.shortTime

@Composable
fun ManagementSupportScreen(
    onNavigateBack: () -> Unit,
    onOpenTicket: (String) -> Unit,
    viewModel: ManagementSupportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val hasFilters = state.search.isNotBlank() || state.statuses.isNotEmpty() ||
        state.categories.isNotEmpty() || state.priorities.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Support tickets", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Outlined.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.search, onValueChange = viewModel::setSearch,
                placeholder = { Text("Search ticket no., subject, student") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            FilterRow(SupportTicketStatus.entries.filter { it != SupportTicketStatus.UNKNOWN }, state.statuses,
                { it.managementLabel() }, viewModel::toggleStatus)
            FilterRow(SupportTicketCategory.selectable, state.categories, { it.label() }, viewModel::toggleCategory)
            FilterRow(SupportTicketPriority.selectable, state.priorities, { it.label() }, viewModel::togglePriority)
            if (hasFilters) TextButton(onClick = viewModel::clearFilters, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Clear filters") }

            when {
                state.isLoading && state.tickets.isEmpty() -> GagLoadingScreen()
                state.error != null && state.tickets.isEmpty() -> GagErrorScreen(message = state.error!!, onRetry = viewModel::reload)
                state.tickets.isEmpty() -> GagEmptyScreen(
                    title = if (hasFilters) "No matching tickets" else "No tickets yet",
                    message = if (hasFilters) "Try removing some filters." else "Student complaints will appear here.",
                    icon = Icons.Outlined.ConfirmationNumber
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item { Text("${state.totalCount} ticket(s)", style = MaterialTheme.typography.labelMedium) }
                    items(state.tickets, key = { it.id }) { t -> QueueRow(t) { onOpenTicket(t.id) } }
                    if (state.canLoadMore) item {
                        OutlinedButton(onClick = viewModel::loadMore, enabled = !state.isLoadingMore, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.isLoadingMore) "Loading…" else "Load more")
                        }
                    }
                    state.error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
                }
            }
        }
    }
}

@Composable
private fun <T> FilterRow(options: List<T>, selected: Set<T>, label: (T) -> String, onToggle: (T) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { o ->
            FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(label(o)) })
        }
    }
}

@Composable
private fun QueueRow(t: SupportTicketSummary, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .semantics { contentDescription = "Ticket ${t.ticketNumber}, ${t.subject}, ${t.status.managementLabel()}" }
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(t.ticketNumber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                StatusChip(t.status, management = true)
            }
            Text(t.subject, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${t.studentName} · ${t.outletName ?: "No outlet"}${t.orderNumber?.let { " · $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${t.category.label()} · ${t.priority.label()} · ${t.assigneeName?.let { "Assigned: $it" } ?: "Unassigned"} · ${shortTime(t.updatedAt)}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
