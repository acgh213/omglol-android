package lol.omg.pocket

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import lol.omg.pocket.data.*
import lol.omg.pocket.ui.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun latePreviousAddressAndLogoutDoNotRestoreSource() = runTest(dispatcher) {
        val repo = object : FeatureRepository {
            override suspend fun saveProfile(scope: AddressScope, content: String, publish: Boolean): ProfileSaved = error("Unexpected profile write")
            override fun resetDemo() {}

            override suspend fun createStatus(scope: AddressScope, draft: StatusDraft) = error("No writes")
            override suspend fun latest(scope: AddressScope) = emptyList<StatusEntry>()
            override suspend fun profile(scope: AddressScope): ProfileSource {
                if (scope.address == "one") withContext(NonCancellable) { delay(1000) }
                return ProfileSource(scope.address)
            }
        }
        val vm = ProfileViewModel(repo); val one = AddressScope(1, "one", false)
        vm.setScope(one); runCurrent(); vm.setScope(one.copy(address = "two")); advanceUntilIdle()
        assertEquals("two", vm.state.value.source?.content)
        vm.setScope(one); runCurrent(); vm.setScope(null); advanceUntilIdle(); assertNull(vm.state.value.source)
    }
    @Test fun hiddenTabDefersReadAndRetryRetainsOldSource() = runTest(dispatcher) {
        var fail = false; var calls = 0
        val repo = object : FeatureRepository {
            override suspend fun saveProfile(scope: AddressScope, content: String, publish: Boolean): ProfileSaved = error("Unexpected profile write")
            override fun resetDemo() {}

            override suspend fun createStatus(scope: AddressScope, draft: StatusDraft) = error("No writes")
            override suspend fun latest(scope: AddressScope) = emptyList<StatusEntry>()
            override suspend fun profile(scope: AddressScope): ProfileSource {
                calls++; if (fail) throw ApiFailure(FailureKind.NETWORK); return ProfileSource("raw")
            }
        }
        val vm = ProfileViewModel(repo); val scope = AddressScope(1, "one", false)
        vm.setScope(scope, false); advanceUntilIdle(); assertEquals(0, calls)
        vm.setScope(scope, true); advanceUntilIdle(); assertEquals("raw", vm.state.value.source?.content)
        fail = true; vm.refresh(); advanceUntilIdle(); assertNotNull(vm.state.value.error); assertEquals("raw", vm.state.value.source?.content)
        fail = false; vm.refresh(); advanceUntilIdle(); assertNull(vm.state.value.error)
    }
}
