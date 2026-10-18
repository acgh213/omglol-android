package lol.omg.pocket

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import lol.omg.pocket.data.*
import lol.omg.pocket.ui.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditorTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun setup(){Dispatchers.setMain(dispatcher)}
    @After fun cleanup(){Dispatchers.resetMain()}
    private val scope=AddressScope(1,"one",false)
    private class Repo:FeatureRepository {
        var remote=ProfileSource("original",theme="custom",css="css",head="head",metadata="metadata",branding="branding",picture="picture")
        var posts=0;var lastPublish:Boolean?=null;var readFailure=false;var postFailure:ApiFailure?=null;var delayPost=false
        override suspend fun createStatus(scope:AddressScope,draft:StatusDraft):PostedStatus=error("No status writes")
        override suspend fun latest(scope:AddressScope)=emptyList<StatusEntry>()
        override suspend fun profile(scope:AddressScope):ProfileSource { if(readFailure)throw ApiFailure(FailureKind.NETWORK);return remote }
        override suspend fun saveProfile(scope:AddressScope,content:String,publish:Boolean):ProfileSaved {
            posts++;lastPublish=publish
            if(delayPost)withContext(NonCancellable){delay(1000)}
            postFailure?.let{throw it};remote=remote.copy(content=content);return ProfileSaved(publish)
        }
        override fun resetDemo(){}
    }
    private suspend fun TestScope.editor(repo:Repo):ProfileViewModel {
        val vm=ProfileViewModel(repo);vm.setScope(scope);advanceUntilIdle();vm.openEditor();return vm
    }
    @Test fun saveDraftThenPublishPreservesConfigurationAndPreventsDoubleTap()=runTest(dispatcher){
        val repo=Repo();val vm=editor(repo);val original=repo.remote
        vm.updateDraft("edited");vm.save(false);vm.save(true);advanceUntilIdle()
        assertEquals(1,repo.posts);assertEquals(false,repo.lastPublish)
        assertEquals(original.copy(content="edited"),vm.state.value.source)
        assertFalse(vm.state.value.dirty);assertTrue(vm.state.value.receipt!!.contains("without publishing"))
        vm.save(true);advanceUntilIdle();assertEquals(2,repo.posts);assertEquals(true,repo.lastPublish)
    }
    @Test fun conflictPreservesDraftAndNewRemoteChangesRequireAnotherReview()=runTest(dispatcher){
        val repo=Repo();val vm=editor(repo);vm.updateDraft("mine");repo.remote=repo.remote.copy(content="theirs")
        vm.save(false);advanceUntilIdle();assertEquals(ProfilePhase.CONFLICT,vm.state.value.phase);assertEquals(0,repo.posts)
        assertEquals("mine",vm.state.value.draft);assertEquals("theirs",vm.state.value.remote?.content)
        repo.remote=repo.remote.copy(content="newer")
        vm.save(false,true);advanceUntilIdle();assertEquals(0,repo.posts);assertEquals("newer",vm.state.value.remote?.content)
        vm.save(false,true);advanceUntilIdle();assertEquals(1,repo.posts);assertEquals("mine",repo.remote.content)
    }
    @Test fun preflightFailureSendsNothingAndRetainsEditableDraft()=runTest(dispatcher){
        val repo=Repo();val vm=editor(repo);vm.updateDraft("mine");repo.readFailure=true
        vm.save(false);advanceUntilIdle();assertEquals(0,repo.posts);assertEquals(ProfilePhase.EDITING,vm.state.value.phase)
        assertEquals("mine",vm.state.value.draft);assertNotNull(vm.state.value.error)
    }
    @Test fun ambiguousPublishNeedsReadbackAndExplicitAcknowledgementNotMatchingTextHeuristic()=runTest(dispatcher){
        val repo=Repo();val vm=editor(repo);vm.updateDraft("mine");repo.postFailure=ApiFailure(FailureKind.NETWORK)
        vm.save(true);advanceUntilIdle();assertEquals(ProfilePhase.UNCERTAIN,vm.state.value.phase)
        vm.save(true);advanceUntilIdle();assertEquals(1,repo.posts)
        vm.resumeAfterCheck();assertEquals(ProfilePhase.UNCERTAIN,vm.state.value.phase)
        repo.remote=repo.remote.copy(content="mine");vm.checkAfterUncertain();advanceUntilIdle()
        assertEquals(ProfilePhase.UNCERTAIN,vm.state.value.phase);assertNull(vm.state.value.receipt)
        vm.resumeAfterCheck();assertEquals(ProfilePhase.EDITING,vm.state.value.phase)
        assertEquals("mine",vm.state.value.draft);assertEquals(1,repo.posts)
    }
    @Test fun closeGuardsDraftAndSessionLossRejectsLateResult()=runTest(dispatcher){
        val repo=Repo();val vm=editor(repo);vm.updateDraft("mine");assertFalse(vm.closeEditor())
        repo.delayPost=true;vm.save(false);runCurrent();assertFalse(vm.closeEditor(true))
        vm.setScope(null);advanceUntilIdle();assertNull(vm.state.value.source);assertFalse(vm.state.value.editorOpen)
        assertEquals("",vm.state.value.draft)
    }
    @Test fun demoEditsAreLocalAddressScopedAndReset()=runTest(dispatcher){
        val repo=LiveFeatureRepository(lol.omg.pocket.security.AccountSession())
        val one=AddressScope(1,"example",true);val two=one.copy(address="little-space")
        val original=repo.profile(one);val other=repo.profile(two)
        assertFalse(repo.saveProfile(one,"demo draft",false).published)
        assertEquals(original.copy(content="demo draft"),repo.profile(one));assertEquals(other,repo.profile(two))
        repo.resetDemo();assertEquals(original,repo.profile(one))
    }
}
