package lol.omg.pocket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lol.omg.pocket.data.*
import lol.omg.pocket.security.CredentialStore

/** No credentials are included in UI state or SavedStateHandle. */
data class AccountUiState(
    val restoring: Boolean = true,
    val busy: Boolean = false,
    val demo: Boolean = false,
    val account: Account? = null,
    val addresses: List<Address> = emptyList(),
    val selected: String? = null,
    val detail: AddressDetail? = null,
    val detailLoading: Boolean = false,
    val error: String? = null,
    val detailError: String? = null,
    val notice: String? = null,
    val canRetry: Boolean = false,
    val sessionGeneration: Int = 0
) {
    val scope: AddressScope?
        get() = selected?.takeIf { name -> account != null && addresses.any { it.name == name } }
            ?.let { AddressScope(sessionGeneration, it, demo) }
}
class AccountViewModel(
    private val live: AccountRepository,
    private val vault: CredentialStore,
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val mutable = MutableStateFlow(AccountUiState())
    val state = mutable.asStateFlow()
    private var credentials: Credentials? = null
    private var repo: AccountRepository = live
    private var work: Job? = null
    private var detailWork: Job? = null
    private var generation = 0
    // Serializes load/save/clear, including when a session is cancelled during IO.
    private val storageMutex = Mutex()
    init {
        val version = generation
        work = viewModelScope.launch {
            try {
                val stored = storage { vault.load() }
                if (version != generation) return@launch
                mutable.update { it.copy(restoring = false) }
                if (stored != null) { credentials = stored; refresh() }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                runCatching { storage { vault.clear() } }
                if (version == generation) mutable.value = AccountUiState(restoring = false, notice = "Saved sign-in couldn’t be read. Please sign in again.")
            }
        }
    }
    fun signIn(email: String, key: String, remember: Boolean = false) {
        if (state.value.busy || state.value.restoring) return
        val normalizedEmail = email.trim()
        val normalizedKey = key.trim()
        if (!normalizedEmail.matches(Regex("^[^\\s/@]+@[^\\s/@]+\\.[^\\s/@]+$")) || normalizedKey.isBlank() || normalizedKey.any { it.code !in 33..126 }) {
            mutable.update { it.copy(error = "Enter your account email and API key.") }; return
        }
        resetSession()
        repo = live; credentials = Credentials(normalizedEmail, normalizedKey)
        loadSnapshot(remember)
    }
    fun demo() {
        if (state.value.busy || state.value.restoring) return
        resetSession(); repo = DemoAccountRepository(); credentials = Credentials("demo@example.com", "demo")
        mutable.value = AccountUiState(restoring = false, demo = true)
        loadSnapshot(false)
    }
    fun refresh() { if (!state.value.busy && credentials != null) loadSnapshot(null) }
    private fun loadSnapshot(remember: Boolean?) {
        val c = credentials ?: return
        val version = generation
        work?.cancel(); detailWork?.cancel()
        mutable.update { it.copy(busy = true, error = null, detailLoading = false, canRetry = false) }
        work = viewModelScope.launch {
            try {
                // Clear any previous saved session before opting into this one.
                if (remember != null && !state.value.demo) storage { vault.clear() }
                val result = repo.snapshot(c)
                ensureActive()
                if (version != generation) return@launch
                var notice = state.value.notice
                if (remember == true) {
                    try { storage { vault.save(c) } }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        runCatching { storage { vault.clear() } }
                        notice = "Signed in for this session. Couldn’t save sign-in securely."
                    }
                }
                if (version != generation) return@launch
                val selection = state.value.selected?.takeIf { name -> result.addresses.any { it.name == name } }
                    ?: result.addresses.firstOrNull()?.name
                mutable.update { it.copy(busy = false, account = result.account, addresses = result.addresses,
                    selected = selection, detail = null, detailError = null, notice = notice, sessionGeneration = version) }
                selection?.let { selectAddress(it) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (version != generation) return@launch
                if (e is ApiFailure && e.kind == FailureKind.AUTH) expire(e.message!!)
                else mutable.update { it.copy(busy = false, error = safeMessage(e), canRetry = true) }
            }
        }
    }
    fun selectAddress(name: String) {
        if (state.value.busy || state.value.addresses.none { it.name == name }) return
        val c = credentials ?: return
        val version = generation
        detailWork?.cancel()
        mutable.update { it.copy(selected = name, detail = null, detailError = null, detailLoading = true) }
        detailWork = viewModelScope.launch {
            try {
                val detail = repo.address(c, name)
                ensureActive()
                if (version == generation && state.value.selected == name) mutable.update { it.copy(detail = detail, detailLoading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (version != generation || state.value.selected != name) return@launch
                if (e is ApiFailure && e.kind == FailureKind.AUTH) expire(e.message!!)
                else mutable.update { it.copy(detailLoading = false, detailError = safeMessage(e)) }
            }
        }
    }
    fun signOut() {
        resetSession()
        mutable.value = AccountUiState(restoring = false, busy = true)
        val version = generation
        work = viewModelScope.launch {
            val notice = try { storage { vault.clear() }; null }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { "Couldn’t remove saved sign-in. Clear this app’s storage in Android Settings." }
            if (version == generation) mutable.value = AccountUiState(restoring = false, notice = notice)
        }
    }
    private suspend fun expire(message: String) {
        // Cancel both requests; cleanup still completes, and its result is generation-guarded.
        generation++; credentials = null
        val version = generation
        work?.cancel(); detailWork?.cancel()
        mutable.value = AccountUiState(restoring = false, busy = true)
        var notice: String? = null
        try { withContext(NonCancellable) { storage { vault.clear() } } }
        catch (_: Exception) { notice = "Couldn’t clear saved sign-in. Clear app storage in Android Settings." }
        if (version == generation) mutable.value = AccountUiState(restoring = false, error = message, notice = notice)
    }
    private fun resetSession() { generation++; work?.cancel(); detailWork?.cancel(); credentials = null; mutable.value = AccountUiState(restoring = false) }
    private suspend fun <T> storage(block: () -> T): T = withContext(storageDispatcher) { storageMutex.withLock { block() } }
    private fun safeMessage(e: Exception) = (e as? ApiFailure)?.message ?: "Something went wrong. Please try again."
}
