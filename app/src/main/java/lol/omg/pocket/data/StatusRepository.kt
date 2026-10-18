package lol.omg.pocket.data

interface StatusRepository {
    suspend fun statuses(address: String): List<StatusEntry>
}
class LiveStatusRepository(private val api: OmgApi = OmgApi()) : StatusRepository {
    override suspend fun statuses(address: String) = api.statuses(address)
}
class DemoStatusRepository : StatusRepository {
    override suspend fun statuses(address: String): List<StatusEntry> = when (address) {
        "example" -> listOf(
            StatusEntry("example-2", address, "☕️", "Coffee, an open window, and a little corner of the internet to call home.", 1792267200),
            StatusEntry("example-1", address, "🌱", "Making small things, one day at a time.", 1792180800))
        "little-space" -> listOf(
            StatusEntry("space-2", address, "🌙", "A quiet evening in my little space.", 1792270800),
            StatusEntry("space-1", address, "✨", "Hello from my other address!", 1792184400))
        else -> emptyList()
    }
}
