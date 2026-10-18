package lol.omg.pocket

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import lol.omg.pocket.data.*
import lol.omg.pocket.security.*
import org.junit.Assert.*
import org.junit.Test

class AccountSessionTest {
    @Test fun capabilityRejectsWrongGenerationUnownedAndDemo() = runTest {
        val session = AccountSession(); val c = Credentials("a@b.co", "hidden-key")
        session.activate(c, 1, setOf("one"), false)
        for ((gen, address) in listOf(2 to "one", 1 to "other")) {
            try { session.withCredentials(gen, address) { fail("Must not expose key") }; fail() } catch (_: SessionChanged) {}
        }
        assertEquals("hidden-key", session.withCredentials(1, "one") { it.apiKey })
        session.activate(c, 2, setOf("one"), true)
        try { session.withCredentials(2, "one") { fail() }; fail() } catch (_: SessionChanged) {}
        assertFalse(session.toString().contains("hidden-key"))
    }
    @Test fun logoutRejectsLateResult() = runTest {
        val session = AccountSession(); session.activate(Credentials("a@b.co", "k"), 1, setOf("one"), false)
        val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val result = async {
            runCatching { session.withCredentials(1, "one") { entered.complete(Unit); finish.await(); "late" } }
        }
        entered.await(); session.clear(); finish.complete(Unit)
        assertTrue(result.await().exceptionOrNull() is SessionChanged)
    }
    @Test fun oldAuthRejectionCannotExpireNewSession() = runTest {
        val session = AccountSession(); var rejected: Int? = null; session.onRejected = { rejected = it }
        session.activate(Credentials("a@b.co", "old"), 1, setOf("one"), false)
        val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val result = async { runCatching { session.withCredentials(1, "one") { entered.complete(Unit); finish.await(); throw ApiFailure(FailureKind.AUTH) } } }
        entered.await(); session.activate(Credentials("a@b.co", "new"), 2, setOf("one"), false); finish.complete(Unit)
        result.await(); assertNull(rejected)
        try { session.withCredentials(2, "one") { throw ApiFailure(FailureKind.AUTH) } } catch (_: ApiFailure) {}
        assertEquals(2, rejected)
    }
}
