package lol.omg.pocket

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import lol.omg.pocket.data.*
import lol.omg.pocket.security.CredentialStore
import lol.omg.pocket.ui.AccountViewModel
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private class Store : CredentialStore {
        var saved: Credentials? = null
        var broken = false
        override fun load() = saved
        override fun save(credentials: Credentials) { if (broken) error("storage"); saved = credentials }
        override fun clear() { saved = null }
    }
    private class Repo : AccountRepository {
        var failure: ApiFailure? = null
        var empty = false
        var slow = false
        override suspend fun snapshot(credentials: Credentials): AccountSnapshot {
            failure?.let { throw it }
            return AccountSnapshot(Account("Cassie", credentials.email, "4 years ago"), if (empty) emptyList() else listOf(Address("one", "Lifetime"), Address("two", "Lifetime")))
        }
        override suspend fun address(credentials: Credentials, name: String): AddressDetail {
            if (slow && name == "one") delay(1000)
            failure?.let { throw it }
            return AddressDetail(name, "4 years ago", "Lifetime", true)
        }
    }
    @Test fun validatesBeforeNetworkAndDoesNotSaveByDefault() = runTest(dispatcher) {
        val store = Store(); val vm = AccountViewModel(Repo(), store, dispatcher)
        advanceUntilIdle()
        vm.signIn("bad", ""); advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertNull(vm.state.value.account)
        vm.signIn("c@example.com", "k"); advanceUntilIdle()
        assertEquals("Cassie", vm.state.value.account?.name)
        assertEquals("one", vm.state.value.detail?.name)
        assertNull(store.saved)
    }
    @Test fun rememberAndLogoutClearStateAndCredentials() = runTest(dispatcher) {
        val store = Store(); val vm = AccountViewModel(Repo(), store, dispatcher)
        advanceUntilIdle(); vm.signIn("c@example.com", "k", true); advanceUntilIdle()
        assertNotNull(store.saved)
        vm.signOut(); advanceUntilIdle()
        assertNull(store.saved); assertNull(vm.state.value.account); assertNull(vm.state.value.detail)
    }
    @Test fun emptyAddressesAndRetryAreUsable() = runTest(dispatcher) {
        val repo = Repo().apply { failure = ApiFailure(FailureKind.NETWORK) }
        val vm = AccountViewModel(repo, Store(), dispatcher); advanceUntilIdle()
        vm.signIn("c@example.com", "k"); advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.canRetry)
        repo.failure = null; repo.empty = true
        vm.refresh(); advanceUntilIdle()
        assertNotNull(vm.state.value.account)
        assertTrue(vm.state.value.addresses.isEmpty())
    }
    @Test fun authExpiryClearsSession() = runTest(dispatcher) {
        val repo = Repo(); val store = Store(); val vm = AccountViewModel(repo, store, dispatcher)
        advanceUntilIdle(); vm.signIn("c@example.com", "k", true); advanceUntilIdle()
        repo.failure = ApiFailure(FailureKind.AUTH)
        vm.refresh(); advanceUntilIdle()
        assertNull(vm.state.value.account); assertNull(store.saved)
        assertNotNull(vm.state.value.error)
    }
    @Test fun latestSelectionWinsAndLogoutCancelsWork() = runTest(dispatcher) {
        val repo = Repo().apply { slow = true }
        val vm = AccountViewModel(repo, Store(), dispatcher); advanceUntilIdle()
        vm.signIn("c@example.com", "k"); runCurrent()
        vm.selectAddress("two"); advanceUntilIdle()
        assertEquals("two", vm.state.value.detail?.name)
        vm.selectAddress("one"); runCurrent(); vm.signOut(); advanceUntilIdle()
        assertNull(vm.state.value.detail); assertNull(vm.state.value.account)
    }
    @Test fun vaultFailureIsVisibleAndLeavesSessionInMemoryOnly() = runTest(dispatcher) {
        val store = Store().apply { broken = true }
        val vm = AccountViewModel(Repo(), store, dispatcher); advanceUntilIdle()
        vm.signIn("c@example.com", "k", true); advanceUntilIdle()
        assertNotNull(vm.state.value.account)
        assertNotNull(vm.state.value.notice)
        assertNull(store.saved)
    }
    @Test fun demoNeverReadsOrWritesLiveCredentials() = runTest(dispatcher) {
        val store = Store(); val vm = AccountViewModel(Repo(), store, dispatcher); advanceUntilIdle()
        vm.demo(); advanceUntilIdle()
        assertTrue(vm.state.value.demo)
        assertNull(store.saved)
        assertNotNull(vm.state.value.account)
    }

    @Test fun expiredSessionCannotOverwriteSignOutWhileStorageIsPending() = runTest(dispatcher) {
        val queue = object : CoroutineDispatcher() {
            val tasks = java.util.ArrayDeque<Runnable>()
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { tasks.add(block) }
            fun step() { tasks.removeFirst().run() }
        }
        val repo = Repo(); val store = Store()
        val vm = AccountViewModel(repo, store, queue)
        runCurrent(); queue.step(); runCurrent()
        vm.signIn("c@example.com", "k"); runCurrent(); queue.step(); runCurrent()
        assertNotNull(vm.state.value.account)
        repo.failure = ApiFailure(FailureKind.AUTH)
        vm.refresh(); runCurrent()
        assertNull("Expired private data must disappear before storage finishes", vm.state.value.account)
        assertTrue(vm.state.value.busy)
        vm.signOut(); runCurrent()
        queue.step(); runCurrent()
        assertTrue("Older expiry completion must not unlock a newer sign-out", vm.state.value.busy)
        queue.step(); runCurrent()
        assertFalse(vm.state.value.busy)
        assertNull(vm.state.value.error)
    }

    @Test fun addressScopeSurvivesRefreshAndChangesOnNewSession() = runTest(dispatcher) {
        val repo = Repo(); val vm = AccountViewModel(repo, Store(), dispatcher)
        advanceUntilIdle(); assertNull(vm.state.value.scope)
        vm.signIn("c@example.com", "k"); advanceUntilIdle()
        vm.selectAddress("two"); advanceUntilIdle()
        val scope = vm.state.value.scope!!
        assertEquals("two", scope.address)
        vm.refresh(); advanceUntilIdle(); assertEquals(scope, vm.state.value.scope)
        vm.signOut(); advanceUntilIdle(); assertNull(vm.state.value.scope)
        vm.signIn("c@example.com", "k"); advanceUntilIdle()
        assertNotEquals(scope.sessionGeneration, vm.state.value.scope!!.sessionGeneration)
        repo.empty = true; vm.refresh(); advanceUntilIdle(); assertNull(vm.state.value.scope)
    }

}
