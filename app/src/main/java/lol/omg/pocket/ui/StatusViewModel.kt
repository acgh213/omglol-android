package lol.omg.pocket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import lol.omg.pocket.data.*

data class AddressScope(val sessionGeneration: Int, val address: String, val demo: Boolean)
data class StatusUiState(
    val scope: AddressScope? = null,
    val entries: List<StatusEntry> = emptyList(),
    val loading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null
)
class StatusViewModel(
    private val live: StatusRepository = LiveStatusRepository(),
    private val demo: StatusRepository = DemoStatusRepository()
) : ViewModel() {
    private val mutable = MutableStateFlow(StatusUiState())
    val state = mutable.asStateFlow()
    private var request = 0
    private var work: Job? = null
    fun setScope(scope: AddressScope?) {
        if (mutable.value.scope == scope) return
        request++; work?.cancel()
        mutable.value = StatusUiState(scope = scope)
        if (scope != null) refresh()
    }
    fun retry() = refresh()
    fun refresh() {
        val scope = mutable.value.scope ?: return
        val version = ++request
        work?.cancel()
        mutable.update { it.copy(loading = true, error = null) }
        work = viewModelScope.launch {
            try {
                val results = (if (scope.demo) demo else live).statuses(scope.address)
                ensureActive()
                if (request == version && mutable.value.scope == scope) mutable.update {
                    it.copy(entries = results, loading = false, hasLoaded = true, error = null)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (request == version && mutable.value.scope == scope) mutable.update {
                    it.copy(loading = false, error = when {
                        e is ApiFailure && e.kind == FailureKind.AUTH -> "Couldn’t access this public Statuslog. Your account is still signed in."
                        e is ApiFailure -> e.message
                        else -> "Couldn’t load this Statuslog. Please try again."
                    })
                }
            }
        }
    }
}
