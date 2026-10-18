package lol.omg.pocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable fun ComposerDialog(state: ComposerState, model: ComposerViewModel) {
    var discard by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(false) }
    val locked = state.phase == PostPhase.SUBMITTING || state.checking
    val editable = state.phase == PostPhase.EDITING
    fun requestClose() {
        if (locked) return
        if (state.dirty) discard = true else model.close(true)
    }
    Dialog(onDismissRequest = ::requestClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("New status", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = ::requestClose, enabled = !locked) { Text(if (state.phase == PostPhase.POSTED) "Done" else "Close") }
                }
                Text(if (state.scope?.demo == true) "Demo · ${state.scope.address}.status.lol" else "Posting publicly to ${state.scope?.address}.status.lol",
                    style = MaterialTheme.typography.titleMedium)
                if (state.phase == PostPhase.POSTED) {
                    MessageCard(if (state.scope?.demo == true) "Saved locally in demo. Nothing was published online." else "Your status was published.")
                    Text("Status ID: ${state.receipt?.id}", style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedTextField(state.draft.emoji, model::updateEmoji, label = { Text("Emoji") }, singleLine = true,
                        enabled = editable, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(state.draft.content, model::updateContent, label = { Text("Status text") }, minLines = 5,
                        enabled = editable, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(state.draft.crossPost, model::updateCrossPost, enabled = editable)
                        Text("Also cross-post to Mastodon", modifier = Modifier.weight(1f))
                    }
                    Text("Uses your omg.lol Mastodon integration if configured. Off by default. Drafts stay in memory until you post, discard, or sign out.",
                        style = MaterialTheme.typography.bodySmall)
                    state.error?.let { MessageCard(it, error = true) }
                    when (state.phase) {
                        PostPhase.SUBMITTING -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Submitting once…") }
                        PostPhase.UNCERTAIN -> {
                            MessageCard("Publication is uncertain. Another attempt could create a duplicate.")
                            OutlinedButton(onClick = model::checkHistory, enabled = !state.checking) { Text(if (state.checking) "Checking history…" else "Check latest statuses") }
                            if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (state.historyChecked) {
                                Text("Latest statuses for ${state.scope?.address}", style = MaterialTheme.typography.titleMedium)
                                if (state.recent.isEmpty()) Text("No statuses returned. A delayed post could still appear later.")
                                state.recent.forEach { entry ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text("${entry.emoji} ${entry.content}")
                                            Text(formatStatusTime(entry.createdEpochSeconds), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                TextButton(onClick = { retry = true }) { Text("I checked; allow another attempt") }
                            }
                        }
                        PostPhase.EDITING -> Button(onClick = model::submit, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.scope?.demo == true) "Post locally (demo)" else "Post to ${state.scope?.address}.status.lol")
                        }
                        PostPhase.POSTED -> Unit
                    }
                }
            }
        }
        if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard this draft?") },
            text = { Text(if (state.phase == PostPhase.UNCERTAIN) "This only removes your local draft. The status may already be published." else "Your unposted text will be removed from this device.") },
            confirmButton = { TextButton(onClick = { model.close(true); discard = false }) { Text("Discard draft") } },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep writing") } })
        if (retry) AlertDialog(onDismissRequest = { retry = false }, title = { Text("Allow another attempt?") },
            text = { Text("Even after checking history, the original post may appear later. Another attempt can create a duplicate. This unlocks editing; it does not post automatically.") },
            confirmButton = { TextButton(onClick = { model.allowAnotherAttempt(); retry = false }) { Text("Allow another attempt") } },
            dismissButton = { TextButton(onClick = { retry = false }) { Text("Keep checking") } })
    }
}
