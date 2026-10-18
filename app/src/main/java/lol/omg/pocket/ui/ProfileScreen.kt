package lol.omg.pocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable fun ProfileScreen(scope: AddressScope?, state: ProfileState, refresh: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Profile source", style = MaterialTheme.typography.headlineMedium)
            Text(scope?.let { "${it.address}.omg.lol" } ?: "Choose an owned address.")
            Text("Read-only source viewer · Editing and publishing will come later.", style = MaterialTheme.typography.bodySmall)
        }
        when {
            scope == null -> item { MessageCard("No address selected.") }
            state.scope != scope -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else -> {
                item { OutlinedButton(onClick = refresh, enabled = !state.loading) { Text(if (state.loading) "Loading profile…" else "Refresh profile") } }
                if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.error?.let { item { MessageCard(if (state.source != null) "Showing last loaded source. $it" else it, error = true) } }
                state.source?.let { source ->
                    item { Text("Type: ${source.type} · Theme: ${source.theme.ifEmpty { "default" }}", style = MaterialTheme.typography.labelLarge) }
                    item { SourceBlock("Page content", source.content, expandedInitially = true) }
                    item { SourceBlock("Custom CSS", source.css) }
                    item { SourceBlock("Head source", source.head) }
                    item { SourceBlock("Metadata", source.metadata) }
                    item { Text("Branding: ${source.branding.ifEmpty { "default" }}\nPicture: ${source.picture.ifEmpty { "Not set" }}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
@Composable private fun SourceBlock(title: String, content: String, expandedInitially: Boolean = false) {
    var expanded by remember(content) { mutableStateOf(expandedInitially) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "$title · Hide" else "$title · Show") }
            if (expanded) SelectionContainer {
                Text(content.ifEmpty { "(empty)" }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
