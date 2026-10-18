package lol.omg.pocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun formatStatusTime(epoch: Long?): String = epoch?.let {
    runCatching {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .format(Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()))
    }.getOrNull()
} ?: "Date unavailable"

@Composable fun StatusScreen(scope: AddressScope?, state: StatusUiState, refresh: () -> Unit, retry: () -> Unit, compose: (() -> Unit)? = null) {
    LazyColumn(Modifier.fillMaxSize().testTag("status-list"), contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Statuslog", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(scope?.let { "From ${it.address}.status.lol" } ?: "Choose an owned address to browse its Statuslog.",
                style = MaterialTheme.typography.bodyMedium)
        }
        if (scope != null && compose != null) item { Button(onClick = compose) { Text("New status") } }
        when {
            scope == null -> item { MessageCard("No address selected. Your Statuslog will appear here when you own an address.") }
            state.scope != scope -> item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading statuses…") }
            else -> {
                item {
                    OutlinedButton(onClick = refresh, enabled = !state.loading) { Text(if (state.loading) "Refreshing statuses…" else "Refresh statuses") }
                }
                if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.error?.let { message -> item {
                    MessageCard(if (state.hasLoaded) "Showing last loaded statuses. $message" else message, error = true)
                    TextButton(onClick = retry, enabled = !state.loading) { Text("Retry statuses") }
                } }
                if (state.hasLoaded && state.entries.isEmpty()) item { MessageCard("No statuses yet. This address has a fresh start.") }
                itemsIndexed(state.entries, key = { index, entry -> "$index:${entry.id}" }) { _, entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (entry.emoji.isNotBlank()) Text(entry.emoji, style = MaterialTheme.typography.headlineMedium)
                            SelectionContainer { Text(entry.content, style = MaterialTheme.typography.bodyLarge) }
                            Text(formatStatusTime(entry.createdEpochSeconds), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
