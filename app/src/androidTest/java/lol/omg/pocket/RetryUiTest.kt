package lol.omg.pocket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import lol.omg.pocket.data.*
import lol.omg.pocket.security.CredentialStore
import lol.omg.pocket.ui.*
import org.junit.Rule
import org.junit.Test

class RetryUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun retryFirstLoadWithoutReenteringKey() {
        val repo = object : AccountRepository {
            var attempts = 0
            override suspend fun snapshot(credentials: Credentials): AccountSnapshot {
                if (attempts++ == 0) throw ApiFailure(FailureKind.NETWORK)
                return AccountSnapshot(Account("Recovered account", credentials.email, "today"), emptyList())
            }
            override suspend fun address(credentials: Credentials, name: String): AddressDetail = error("No addresses")
        }
        val vault = object : CredentialStore {
            override fun load(): Credentials? = null
            override fun save(credentials: Credentials) {}
            override fun clear() {}
        }
        val vm = AccountViewModel(repo, vault)
        compose.setContent { PocketApp(vm) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Try demo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Account email").performTextInput("test@example.com")
        compose.onNodeWithText("API key").performTextInput("not-a-real-key")
        compose.onNodeWithText("Connect account").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Retry connection").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Retry connection").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Recovered account").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("No addresses belong to this account yet.").performScrollTo().assertIsDisplayed()
    }
}
