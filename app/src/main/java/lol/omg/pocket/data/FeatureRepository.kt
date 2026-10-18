package lol.omg.pocket.data

import lol.omg.pocket.security.AccountSession
import lol.omg.pocket.ui.AddressScope

interface FeatureRepository {
    suspend fun createStatus(scope: AddressScope, draft: StatusDraft): PostedStatus
    suspend fun latest(scope: AddressScope): List<StatusEntry>
    suspend fun profile(scope: AddressScope): ProfileSource
}
class LiveFeatureRepository(
    private val session: AccountSession,
    private val api: OmgApi = OmgApi(),
    private val demo: DemoStatusRepository = DemoStatusRepository()
) : FeatureRepository {
    override suspend fun createStatus(scope: AddressScope, draft: StatusDraft): PostedStatus {
        require(draft.content.isNotBlank())
        if (scope.demo) return demo.post(scope.address, draft)
        return session.withCredentials(scope.sessionGeneration, scope.address) { api.createStatus(it, scope.address, draft) }
    }
    override suspend fun latest(scope: AddressScope) = if (scope.demo) demo.statuses(scope.address) else api.statuses(scope.address)
    override suspend fun profile(scope: AddressScope): ProfileSource {
        if (scope.demo) return ProfileSource("# ${scope.address}\n\nWelcome to my little corner of the internet.\n\n- https://${scope.address}.omg.lol", theme = "default", css = "/* Your custom CSS lives here. */", metadata = "{\"title\":\"Demo profile\"}")
        return session.withCredentials(scope.sessionGeneration, scope.address) { api.profile(it, scope.address) }
    }
}
