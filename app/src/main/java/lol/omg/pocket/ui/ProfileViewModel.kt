package lol.omg.pocket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import lol.omg.pocket.data.*

enum class ProfilePhase { EDITING, CHECKING, SAVING, CONFLICT, UNCERTAIN, VERIFYING }
data class ProfileState(
    val scope: AddressScope? = null,
    val source: ProfileSource? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val editorOpen: Boolean = false,
    val draft: String = "",
    val baseline: ProfileSource? = null,
    val phase: ProfilePhase = ProfilePhase.EDITING,
    val pendingPublish: Boolean? = null,
    val remote: ProfileSource? = null,
    val checkedAfterUncertain: Boolean = false,
    val receipt: String? = null
) {
    val dirty get() = baseline != null && draft != baseline.content
    val busy get() = phase in setOf(ProfilePhase.CHECKING, ProfilePhase.SAVING, ProfilePhase.VERIFYING)
    val needsDiscard get() = dirty || phase in setOf(ProfilePhase.CONFLICT, ProfilePhase.UNCERTAIN)
}
class ProfileViewModel(private val repository: FeatureRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ProfileState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var generation = 0
    fun setScope(scope: AddressScope?, visible: Boolean = true) {
        val old = state.value.scope
        if (scope != old) {
            generation++; work?.cancel()
            if (old?.demo == true && (scope == null || scope.sessionGeneration != old.sessionGeneration)) repository.resetDemo()
            mutable.value = ProfileState(scope)
        }
        if (visible && scope != null && state.value.source == null && !state.value.loading && state.value.error == null && !state.value.editorOpen) refresh()
    }
    fun refresh() {
        val scope = state.value.scope ?: return
        if (state.value.editorOpen) return
        val version = ++generation
        work?.cancel(); mutable.update { it.copy(loading = true, error = null) }
        work = viewModelScope.launch {
            try {
                val result = repository.profile(scope)
                ensureActive()
                if (version == generation && state.value.scope == scope) mutable.update { it.copy(source = result, loading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (version == generation && state.value.scope == scope) mutable.update {
                    it.copy(loading = false, error = (e as? ApiFailure)?.message ?: "Couldn’t load the profile source. Please try again.")
                }
            }
        }
    }
    fun openEditor() {
        val current = state.value
        val source = current.source ?: return
        if (current.scope == null || current.loading || current.editorOpen) return
        mutable.update { it.copy(editorOpen = true, draft = source.content, baseline = source, error = null, receipt = null,
            phase = ProfilePhase.EDITING, remote = null, pendingPublish = null, checkedAfterUncertain = false) }
    }
    fun updateDraft(value: String) {
        if (state.value.editorOpen && state.value.phase == ProfilePhase.EDITING) mutable.update { it.copy(draft = value, error = null, receipt = null) }
    }
    fun closeEditor(discard: Boolean = false): Boolean {
        val current = state.value
        if (current.busy || (!discard && current.needsDiscard)) return false
        generation++; work?.cancel()
        mutable.value = ProfileState(scope = current.scope, source = current.remote ?: current.source,
            error = if (current.phase == ProfilePhase.UNCERTAIN) "Last save outcome is unconfirmed. Refresh to check saved source; publishing cannot be verified here." else current.error)
        return true
    }
    fun useLatestSource() {
        val current = state.value
        val latest = current.remote ?: return
        if (current.phase != ProfilePhase.CONFLICT) return
        mutable.update { it.copy(source = latest, baseline = latest, draft = latest.content, remote = null,
            phase = ProfilePhase.EDITING, pendingPublish = null, error = null, receipt = "Loaded latest saved source.") }
    }
    fun save(publish: Boolean, overwrite: Boolean = false) {
        val current = state.value
        val scope = current.scope ?: return
        if (!current.editorOpen || current.busy) return
        if (overwrite) {
            if (current.phase != ProfilePhase.CONFLICT || current.remote == null || current.pendingPublish != publish) return
        } else if (current.phase != ProfilePhase.EDITING) return
        val expected = (if (overwrite) current.remote else current.baseline) ?: return
        val version = ++generation
        mutable.update { it.copy(phase = ProfilePhase.CHECKING, error = null, receipt = null, pendingPublish = publish) }
        work = viewModelScope.launch {
            var submitted = false
            try {
                val latest = repository.profile(scope)
                ensureActive()
                if (version != generation || state.value.scope != scope) return@launch
                if (latest != expected) {
                    mutable.update { it.copy(phase = ProfilePhase.CONFLICT, remote = latest, error = "This profile changed since you loaded it. Your draft is preserved.") }
                    return@launch
                }
                mutable.update { it.copy(phase = ProfilePhase.SAVING) }
                submitted = true
                repository.saveProfile(scope, current.draft, publish)
                ensureActive()
                if (version == generation && state.value.scope == scope) {
                    val saved = latest.copy(content = current.draft)
                    mutable.update { it.copy(source = saved, baseline = saved, phase = ProfilePhase.EDITING, remote = null,
                        pendingPublish = null, checkedAfterUncertain = false,
                        receipt = if (scope.demo) (if (publish) "Published locally in demo. Nothing changed online." else "Saved locally in demo without publishing.")
                            else (if (publish) "Published to ${scope.address}.omg.lol." else "Saved to omg.lol without publishing.")) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (version != generation || state.value.scope != scope) return@launch
                val definite = e is ApiFailure && e.kind in setOf(FailureKind.AUTH, FailureKind.REJECTED, FailureKind.RATE_LIMIT)
                mutable.update { it.copy(phase = if (submitted && !definite) ProfilePhase.UNCERTAIN else ProfilePhase.EDITING,
                    remote = null, checkedAfterUncertain = false,
                    error = when {
                        !submitted -> "Couldn’t check the latest source. Nothing was submitted; your draft is preserved."
                        definite -> e.message
                        else -> "The save result is unknown. It may have succeeded. Read back the saved source before considering another attempt."
                    }) }
            }
        }
    }
    fun checkAfterUncertain() {
        val current = state.value
        val scope = current.scope ?: return
        if (current.phase != ProfilePhase.UNCERTAIN) return
        val version = ++generation
        mutable.update { it.copy(phase = ProfilePhase.VERIFYING, error = null) }
        work = viewModelScope.launch {
            try {
                val latest = repository.profile(scope)
                ensureActive()
                if (version == generation && state.value.scope == scope) mutable.update { it.copy(phase = ProfilePhase.UNCERTAIN,
                    remote = latest, checkedAfterUncertain = true) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (version == generation && state.value.scope == scope) mutable.update { it.copy(phase = ProfilePhase.UNCERTAIN,
                    checkedAfterUncertain = false, error = "Couldn’t read back saved source. The previous result is still unknown.") }
            }
        }
    }
    fun resumeAfterCheck() {
        val current = state.value
        val latest = current.remote ?: return
        if (current.phase != ProfilePhase.UNCERTAIN || !current.checkedAfterUncertain) return
        mutable.update { it.copy(source = latest, baseline = latest, phase = ProfilePhase.EDITING, remote = null,
            checkedAfterUncertain = false, pendingPublish = null, error = "Previous publication state remains unconfirmed. No request was repeated.") }
    }
}
