package lol.omg.pocket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import lol.omg.pocket.data.Credentials
import lol.omg.pocket.security.KeystoreCredentialStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeviceSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun demoSelectRefreshAndExit() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Try demo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Try demo").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Example Person").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("little-space.omg.lol").performScrollTo().performClick()
        compose.onNodeWithText("Refresh").performScrollTo().performClick()
        compose.onNodeWithText("Exit demo").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Try demo").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun vaultRoundTripContainsNoPlaintextAndClears() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = KeystoreCredentialStore(context)
        vault.clear()
        val c = Credentials("test@example.com", "device-test-not-a-real-key")
        vault.save(c)
        assertEquals(c.email, vault.load()?.email)
        assertEquals(c.apiKey, vault.load()?.apiKey)
        val ciphertext = context.getSharedPreferences("credential_vault", Context.MODE_PRIVATE).all.toString()
        assertFalse(ciphertext.contains(c.email)); assertFalse(ciphertext.contains(c.apiKey))
        vault.clear(); assertNull(vault.load())
    }
}
