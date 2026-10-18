package lol.omg.pocket.data

// Intentionally not a data class: generated toString/copy must not expose secrets.
class Credentials(val email: String, val apiKey: String) {
    override fun toString() = "Credentials([redacted])"
}
data class Account(val name: String, val email: String, val created: String)
data class Address(val name: String, val expiration: String)
data class AddressDetail(val name: String, val registered: String, val expiration: String, val verified: Boolean?)
data class AccountSnapshot(val account: Account, val addresses: List<Address>)
enum class FailureKind { AUTH, NETWORK, RATE_LIMIT, SERVER, FORMAT }
class ApiFailure(val kind: FailureKind) : Exception(when (kind) {
    FailureKind.AUTH -> "Your key was rejected or lacks access. Sign in again with a valid API key."
    FailureKind.NETWORK -> "Couldn’t reach omg.lol. Check your connection and try again."
    FailureKind.RATE_LIMIT -> "omg.lol is limiting requests. Wait a moment, then try again."
    FailureKind.SERVER -> "omg.lol couldn’t complete this request. Please try again later."
    FailureKind.FORMAT -> "omg.lol returned an unexpected response. Please try again later."
})
