package lol.omg.pocket.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

interface AccountRepository {
    suspend fun snapshot(credentials: Credentials): AccountSnapshot
    suspend fun address(credentials: Credentials, name: String): AddressDetail
}
class LiveAccountRepository(private val api: OmgApi = OmgApi()) : AccountRepository {
    override suspend fun snapshot(credentials: Credentials): AccountSnapshot = coroutineScope {
        val account = async { api.account(credentials) }
        val addresses = async { api.addresses(credentials) }
        AccountSnapshot(account.await(), addresses.await())
    }
    override suspend fun address(credentials: Credentials, name: String) = api.address(credentials, name)
}
class DemoAccountRepository : AccountRepository {
    override suspend fun snapshot(credentials: Credentials) = AccountSnapshot(
        Account("Example Person", "hello@example.com", "4 years ago"),
        listOf(Address("example", "Does not expire"), Address("little-space", "Expires in 8 months")))
    override suspend fun address(credentials: Credentials, name: String) = AddressDetail(
        name, "4 years ago", if (name == "example") "Does not expire" else "Expires in 8 months", name == "example")
}
