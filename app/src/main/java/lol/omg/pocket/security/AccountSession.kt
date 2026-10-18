package lol.omg.pocket.security

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import lol.omg.pocket.data.*

class SessionChanged : Exception("The active account or address changed. Please try again.")

/** In-memory capability. Credentials never enter a StateFlow or feature UI model. Main-thread owner. */
class AccountSession {
    private class Active(val credentials: Credentials, val generation: Int, val owned: Set<String>, val demo: Boolean)
    private var active: Active? = null
    var onRejected: (Int) -> Unit = {}
    fun activate(credentials: Credentials, generation: Int, owned: Set<String>, demo: Boolean) {
        active = Active(credentials, generation, owned.toSet(), demo)
    }
    fun clear() { active = null }
    private fun matches(captured: Active, generation: Int, address: String): Boolean {
        val now = active ?: return false
        return !now.demo && now.generation == generation && now.credentials === captured.credentials && address in now.owned
    }
    suspend fun <T> withCredentials(generation: Int, address: String, block: suspend (Credentials) -> T): T {
        val captured = active ?: throw SessionChanged()
        if (!matches(captured, generation, address)) throw SessionChanged()
        val result = try { block(captured.credentials) }
        catch (e: ApiFailure) {
            if (e.kind == FailureKind.AUTH && matches(captured, generation, address)) onRejected(generation)
            throw e
        }
        currentCoroutineContext().ensureActive()
        if (!matches(captured, generation, address)) throw SessionChanged()
        return result
    }
    override fun toString() = "AccountSession([redacted])"
}
