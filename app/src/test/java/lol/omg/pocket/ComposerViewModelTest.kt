package lol.omg.pocket

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import lol.omg.pocket.data.*
import lol.omg.pocket.ui.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ComposerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private val scope = AddressScope(1, "one", false)
    private class Repo : FeatureRepository {
        var posts = 0; var failure: ApiFailure? = null
        var last: StatusDraft? = null; var delayPost = false
        override suspend fun createStatus(scope: AddressScope, draft: StatusDraft): PostedStatus {
            posts++; last = draft
            if (delayPost) withContext(NonCancellable) { delay(1000) }
            failure?.let { throw it }
            return PostedStatus("new", scope.address)
        }
        override suspend fun latest(scope: AddressScope) = listOf(StatusEntry("recent", scope.address, "✨", "Recent", 10))
        override suspend fun profile(scope: AddressScope) = ProfileSource("source")
    }
    @Test fun blankContentAndDoubleTapNeverDuplicatePosts() = runTest(dispatcher) {
        val repo = Repo(); val vm = ComposerViewModel(repo)
        vm.setScope(scope); vm.open(); vm.submit(); advanceUntilIdle(); assertEquals(0, repo.posts)
        vm.updateContent("Hello"); vm.submit(); vm.submit(); advanceUntilIdle()
        assertEquals(1, repo.posts); assertEquals(false, repo.last?.crossPost)
        assertEquals(PostPhase.POSTED, vm.state.value.phase)
        assertEquals("", vm.state.value.draft.content)
    }
    @Test fun ambiguousOutcomePreservesDraftAndRequiresHistoryAndAcknowledgement() = runTest(dispatcher) {
        val repo = Repo().apply { failure = ApiFailure(FailureKind.NETWORK) }; val vm = ComposerViewModel(repo)
        vm.setScope(scope); vm.open(); vm.updateContent("Hello"); vm.submit(); advanceUntilIdle()
        assertEquals(PostPhase.UNCERTAIN, vm.state.value.phase); assertEquals("Hello", vm.state.value.draft.content)
        vm.submit(); advanceUntilIdle(); assertEquals(1, repo.posts)
        vm.allowAnotherAttempt(); assertEquals(PostPhase.UNCERTAIN, vm.state.value.phase)
        vm.checkHistory(); advanceUntilIdle(); assertTrue(vm.state.value.historyChecked)
        assertEquals(1, repo.posts)
        vm.allowAnotherAttempt(); repo.failure = null; vm.submit(); advanceUntilIdle()
        assertEquals(2, repo.posts); assertEquals(PostPhase.POSTED, vm.state.value.phase)
    }
    @Test fun definiteRejectionKeepsEditableDraftAndCrossPostChoice() = runTest(dispatcher) {
        val repo = Repo().apply { failure = ApiFailure(FailureKind.REJECTED) }; val vm = ComposerViewModel(repo)
        vm.setScope(scope); vm.open(); vm.updateContent("Keep me"); vm.updateCrossPost(true)
        vm.submit(); advanceUntilIdle()
        assertEquals(PostPhase.EDITING, vm.state.value.phase)
        assertEquals("Keep me", vm.state.value.draft.content); assertTrue(vm.state.value.draft.crossPost)
    }
    @Test fun sessionSwitchClearsDraftAndLateSuccessCannotRestoreIt() = runTest(dispatcher) {
        val repo = Repo().apply { delayPost = true }; val vm = ComposerViewModel(repo)
        vm.setScope(scope); vm.open(); vm.updateContent("old"); vm.submit(); runCurrent()
        vm.setScope(null); advanceUntilIdle()
        assertNull(vm.state.value.scope); assertFalse(vm.state.value.open); assertEquals("", vm.state.value.draft.content)
    }
    @Test fun demoPostAppearsInSharedLocalHistoryWithoutCredentials() = runTest(dispatcher) {
        val demo = DemoStatusRepository()
        val repo = LiveFeatureRepository(lol.omg.pocket.security.AccountSession(), demo = demo)
        val s = AddressScope(1, "example", true)
        val vm = ComposerViewModel(repo); vm.setScope(s); vm.open(); vm.updateContent("Demo post"); vm.submit(); advanceUntilIdle()
        assertEquals(PostPhase.POSTED, vm.state.value.phase)
        assertEquals("Demo post", repo.latest(s).first().content)
    }
}
