package lol.omg.pocket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import lol.omg.pocket.data.*

enum class PostPhase { EDITING, SUBMITTING, UNCERTAIN, POSTED }
data class ComposerState(
    val scope: AddressScope? = null,
    val open: Boolean = false,
    val draft: StatusDraft = StatusDraft(),
    val phase: PostPhase = PostPhase.EDITING,
    val error: String? = null,
    val receipt: PostedStatus? = null,
    val checking: Boolean = false,
    val historyChecked: Boolean = false,
    val recent: List<StatusEntry> = emptyList()
) {
    val dirty get() = draft != StatusDraft()
}
class ComposerViewModel(private val repository: FeatureRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ComposerState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var generation = 0
    fun setScope(scope: AddressScope?) {
        if (scope == state.value.scope) return
        generation++; work?.cancel(); mutable.value = ComposerState(scope = scope)
    }
    fun open() { if (state.value.scope != null) mutable.update { it.copy(open = true) } }
    fun close(discard: Boolean = false): Boolean {
        if (state.value.phase == PostPhase.SUBMITTING || state.value.checking) return false
        if (!discard && state.value.dirty) return false
        generation++; work?.cancel(); mutable.value = ComposerState(scope = state.value.scope)
        return true
    }
    private fun edit(change: (StatusDraft) -> StatusDraft) {
        if (state.value.phase == PostPhase.EDITING) mutable.update { it.copy(draft = change(it.draft), error = null) }
    }
    fun updateContent(value: String) = edit { it.copy(content = value) }
    fun updateEmoji(value: String) = edit { it.copy(emoji = value) }
    fun updateCrossPost(value: Boolean) = edit { it.copy(crossPost = value) }
    fun submit() {
        val current = state.value
        val scope = current.scope ?: return
        if (!current.open || current.phase != PostPhase.EDITING) return
        if (current.draft.content.isBlank()) { mutable.update { it.copy(error = "Write a status before posting.") }; return }
        val version = ++generation
        mutable.update { it.copy(phase = PostPhase.SUBMITTING, error = null) }
        work = viewModelScope.launch {
            try {
                val receipt = repository.createStatus(scope, current.draft)
                ensureActive()
                if (generation == version && state.value.scope == scope) mutable.update {
                    it.copy(phase = PostPhase.POSTED, receipt = receipt, draft = StatusDraft())
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation != version || state.value.scope != scope) return@launch
                val definite = e is ApiFailure && e.kind in setOf(FailureKind.REJECTED, FailureKind.AUTH, FailureKind.RATE_LIMIT)
                mutable.update { it.copy(phase = if (definite) PostPhase.EDITING else PostPhase.UNCERTAIN,
                    error = if (definite) e.message else "The result is unknown. Your status may already be published. Check latest statuses before considering another attempt.") }
            }
        }
    }
    fun checkHistory() {
        val current = state.value
        val scope = current.scope ?: return
        if (current.phase != PostPhase.UNCERTAIN || current.checking) return
        val version = ++generation
        mutable.update { it.copy(checking = true, error = null) }
        work = viewModelScope.launch {
            try {
                val recent = repository.latest(scope).take(5)
                ensureActive()
                if (version == generation && state.value.scope == scope) mutable.update {
                    it.copy(checking = false, historyChecked = true, recent = recent)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (version == generation && state.value.scope == scope) mutable.update {
                    it.copy(checking = false, error = "Couldn’t check the latest statuses. The original post’s result is still unknown.")
                }
            }
        }
    }
    /** Called only after a separate UI confirmation of possible duplicates. Never submits. */
    fun allowAnotherAttempt() {
        if (state.value.phase == PostPhase.UNCERTAIN && state.value.historyChecked && !state.value.checking) {
            mutable.update { it.copy(phase = PostPhase.EDITING, error = null, historyChecked = false, recent = emptyList()) }
        }
    }
}
