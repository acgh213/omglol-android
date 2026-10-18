package lol.omg.pocket.data

interface StatusRepository {
    suspend fun statuses(address: String): List<StatusEntry>
}
class LiveStatusRepository(private val api: OmgApi = OmgApi()) : StatusRepository {
    override suspend fun statuses(address: String) = api.statuses(address)
}
class DemoStatusRepository : StatusRepository {
    private val posted = mutableMapOf<String, MutableList<StatusEntry>>()
    fun post(address: String, draft: StatusDraft): PostedStatus {
        val id = java.util.UUID.randomUUID().toString()
        posted.getOrPut(address) { mutableListOf() }.add(0, StatusEntry(id, address, draft.emoji, draft.content, java.time.Instant.now().epochSecond))
        return PostedStatus(id, address)
    }
    fun clearPosts() { posted.clear() }
    override suspend fun statuses(address: String): List<StatusEntry> = posted[address].orEmpty() + samples(address)
    private fun samples(address: String): List<StatusEntry> = when (address) {
        "example" -> listOf(
            StatusEntry("example-2", address, "☕️", "Coffee, an open window, and a little corner of the internet to call home.", 1792267200),
            StatusEntry("example-1", address, "🌱", "Making small things, one day at a time.", 1792180800))
        "little-space" -> listOf(
            StatusEntry("space-2", address, "🌙", "A quiet evening in my little space.", 1792270800),
            StatusEntry("space-1", address, "✨", "Hello from my other address!", 1792184400))
        else -> emptyList()
    }
}
