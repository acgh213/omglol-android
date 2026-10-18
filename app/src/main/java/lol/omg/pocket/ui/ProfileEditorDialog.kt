package lol.omg.pocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable fun ProfileEditorDialog(state: ProfileState, model: ProfileViewModel) {
    var confirmation by remember { mutableStateOf<String?>(null) }
    fun close() { if (!state.busy) { if (state.needsDiscard) confirmation = "discard" else model.closeEditor() } }
    val address = "${state.scope?.address}.omg.lol"
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Edit page source", style = MaterialTheme.typography.headlineSmall)
                Text(if (state.scope?.demo == true) "Demo · $address" else address)
                TextButton(onClick = ::close, enabled = !state.busy) { Text("Close") }
                Text("Edit page content here. This editor changes page content only. Drafts stay in memory until you close or sign out.")
                OutlinedTextField(state.draft, model::updateDraft, label = { Text("Page source") }, minLines = 10,
                    enabled = state.phase == ProfilePhase.EDITING, modifier = Modifier.fillMaxWidth())
                state.error?.let { MessageCard(it, error = true) }
                state.receipt?.let { MessageCard(it) }
                when (state.phase) {
                    ProfilePhase.EDITING -> {
                        OutlinedButton(onClick = { if (state.draft.isEmpty()) confirmation = "empty" else model.save(false) }) { Text("Save draft") }
                        Button(onClick = { confirmation = "publish" }) { Text("Publish page") }
                        Text("Save draft stores source without publishing. Publish updates the public page.", style = MaterialTheme.typography.bodySmall)
                    }
                    ProfilePhase.CHECKING, ProfilePhase.SAVING, ProfilePhase.VERIFYING -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(when (state.phase) { ProfilePhase.CHECKING -> "Checking latest source…"; ProfilePhase.SAVING -> "Submitting once…"; else -> "Reading back source…" })
                    }
                    ProfilePhase.CONFLICT -> {
                        Text("Latest saved content", style = MaterialTheme.typography.titleMedium)
                        SelectionContainer { Text(state.remote?.content.orEmpty().ifEmpty { "(empty)" }) }
                        OutlinedButton(onClick = { confirmation = "latest" }) { Text("Load latest source") }
                        Button(onClick = { confirmation = "overwrite" }) { Text("Replace page content…") }
                        Text("The latest source is checked again before replacement. Changes made during the final save can still overlap.")
                    }
                    ProfilePhase.UNCERTAIN -> {
                        OutlinedButton(onClick = model::checkAfterUncertain) { Text("Read back saved source") }
                        if (state.checkedAfterUncertain) {
                            Text(if (state.remote?.content == state.draft) "Saved content matches your draft." else "Saved content differs from your draft.")
                            Text("This cannot confirm whether the public page was published.")
                            SelectionContainer { Text(state.remote?.content.orEmpty().ifEmpty { "(empty)" }) }
                            TextButton(onClick = { confirmation = "resume" }) { Text("Continue editing after check") }
                        }
                    }
                }
            }
        }
        confirmation?.let { action ->
            val message = when (action) {
                "discard" -> "Remove your local draft? Any request already sent may have succeeded."
                "latest" -> "Replace your local draft with the latest saved content?"
                "resume" -> "The previous save or publication remains unconfirmed. This unlocks editing without repeating any request."
                "overwrite" -> "Replace the saved page content for $address with your draft${if (state.pendingPublish == true) " and publish it publicly" else " without publishing"}?"
                "empty" -> "Save empty page content for $address without publishing?"
                else -> "Publish this page content publicly to $address?"
            }
            AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("Confirm action") },
                text = { Text(message + if (state.draft.isEmpty() && action in listOf("publish", "overwrite")) " Your page content is empty." else "") },
                confirmButton = { TextButton(onClick = {
                    when (action) {
                        "discard" -> model.closeEditor(true)
                        "latest" -> model.useLatestSource()
                        "resume" -> model.resumeAfterCheck()
                        "overwrite" -> model.save(state.pendingPublish == true, true)
                        "empty" -> model.save(false)
                        else -> model.save(true)
                    }; confirmation = null
                }) { Text("Confirm") } },
                dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } })
        }
    }
}
