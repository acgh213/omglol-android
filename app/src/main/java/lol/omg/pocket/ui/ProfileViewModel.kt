package lol.omg.pocket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import lol.omg.pocket.data.*

data class ProfileState(val scope: AddressScope? = null, val source: ProfileSource? = null, val loading: Boolean = false, val error: String? = null)
class ProfileViewModel(private val repository: FeatureRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ProfileState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var generation = 0
    fun setScope(scope: AddressScope?, visible: Boolean = true) {
        if (scope != state.value.scope) {
            generation++; work?.cancel(); mutable.value = ProfileState(scope)
        }
        if (visible && scope != null && state.value.source == null && !state.value.loading && state.value.error == null) refresh()
    }
    fun refresh() {
        val scope = state.value.scope ?: return
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
}
