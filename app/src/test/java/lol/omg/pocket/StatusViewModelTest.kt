package lol.omg.pocket

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import lol.omg.pocket.data.*
import lol.omg.pocket.ui.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class StatusViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private val scopeA = AddressScope(1, "one", false)
    private fun entry(address: String, text: String = address) = StatusEntry(text, address, "☕", text, 100)
    @Test fun latestAddressWinsEvenIfOldRequestIgnoresCancellation() = runTest(dispatcher) {
        val repo = object : StatusRepository {
            override suspend fun statuses(address: String): List<StatusEntry> {
                if (address == "one") withContext(NonCancellable) { delay(1000) }
                return listOf(entry(address))
            }
        }
        val vm = StatusViewModel(repo)
        vm.setScope(scopeA); runCurrent()
        vm.setScope(scopeA.copy(address = "two")); advanceUntilIdle()
        assertEquals("two", vm.state.value.entries.single().address)
        assertFalse(vm.state.value.loading)
    }
    @Test fun logoutClearsAndRejectsLateCompletion() = runTest(dispatcher) {
        val repo = object : StatusRepository {
            override suspend fun statuses(address: String): List<StatusEntry> {
                withContext(NonCancellable) { delay(1000) }; return listOf(entry(address))
            }
        }
        val vm = StatusViewModel(repo)
        vm.setScope(scopeA); runCurrent(); vm.setScope(null); advanceUntilIdle()
        assertNull(vm.state.value.scope); assertTrue(vm.state.value.entries.isEmpty())
    }
    @Test fun retryRecoversAndRefreshFailureRetainsSameAddressData() = runTest(dispatcher) {
        var fail = true
        val repo = object : StatusRepository {
            override suspend fun statuses(address: String): List<StatusEntry> {
                if (fail) throw ApiFailure(FailureKind.NETWORK)
                return listOf(entry(address))
            }
        }
        val vm = StatusViewModel(repo)
        vm.setScope(scopeA); advanceUntilIdle()
        assertNotNull(vm.state.value.error); assertFalse(vm.state.value.hasLoaded)
        fail = false; vm.retry(); advanceUntilIdle()
        assertTrue(vm.state.value.hasLoaded); assertNull(vm.state.value.error)
        fail = true; vm.refresh(); advanceUntilIdle()
        assertEquals("one", vm.state.value.entries.single().address)
        assertTrue(vm.state.value.hasLoaded); assertNotNull(vm.state.value.error)
    }
    @Test fun rapidRefreshUsesNewestRequestAndSameScopeDoesNotReload() = runTest(dispatcher) {
        var calls = 0
        val repo = object : StatusRepository {
            override suspend fun statuses(address: String): List<StatusEntry> {
                val number = ++calls
                if (number == 2) withContext(NonCancellable) { delay(1000) }
                return listOf(entry(address, "$number"))
            }
        }
        val vm = StatusViewModel(repo)
        vm.setScope(scopeA); advanceUntilIdle(); vm.setScope(scopeA); advanceUntilIdle()
        assertEquals(1, calls)
        vm.refresh(); runCurrent(); vm.refresh(); advanceUntilIdle()
        assertEquals("3", vm.state.value.entries.single().content)
    }
    @Test fun demoAndNullScopeNeverCallLiveRepository() = runTest(dispatcher) {
        var calls = 0
        val live = object : StatusRepository {
            override suspend fun statuses(address: String): List<StatusEntry> { calls++; error("No live call expected") }
        }
        val vm = StatusViewModel(live)
        vm.setScope(null); vm.refresh(); advanceUntilIdle(); assertEquals(0, calls)
        vm.setScope(AddressScope(1, "example", true)); advanceUntilIdle()
        assertTrue(vm.state.value.entries.isNotEmpty()); assertEquals(0, calls)
        vm.setScope(AddressScope(2, "little-space", true)); advanceUntilIdle()
        assertTrue(vm.state.value.entries.all { it.address == "little-space" })
    }
    @Test fun publicAuthFailureIsLocalToFeature() = runTest(dispatcher) {
        val vm = StatusViewModel(object : StatusRepository {
            override suspend fun statuses(address: String): List<StatusEntry> = throw ApiFailure(FailureKind.AUTH)
        })
        vm.setScope(scopeA); advanceUntilIdle()
        assertEquals(scopeA, vm.state.value.scope)
        assertFalse(vm.state.value.error!!.contains("Sign in again"))
    }
    @Test fun newSessionWithSameAddressClearsOldDataAndReloads() = runTest(dispatcher) {
        var calls = 0
        val vm = StatusViewModel(object : StatusRepository {
            override suspend fun statuses(address: String) = listOf(entry(address, "${++calls}"))
        })
        vm.setScope(scopeA); advanceUntilIdle()
        vm.setScope(scopeA.copy(sessionGeneration = 2))
        assertTrue(vm.state.value.entries.isEmpty())
        advanceUntilIdle(); assertEquals("2", vm.state.value.entries.single().content)
    }
}
