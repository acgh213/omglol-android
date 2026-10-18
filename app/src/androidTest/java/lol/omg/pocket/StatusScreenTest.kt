package lol.omg.pocket

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import lol.omg.pocket.data.StatusEntry
import lol.omg.pocket.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class StatusScreenTest {
    @get:Rule val compose = createComposeRule()
    private val scope = AddressScope(1, "example", true)
    @Test fun longHistoryScrollsAtLargeFontWithoutNestedScrollCrash() {
        val entries = (0..99).map { StatusEntry("$it", "example", "☕", "Entry $it: " + "Long status text. ".repeat(20), null) }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MaterialTheme { StatusScreen(scope, StatusUiState(scope, entries, hasLoaded = true), {}, {}) }
            }
        }
        compose.onNodeWithTag("status-list").performScrollToIndex(101)
        compose.onNodeWithText(entries.last().content).assertIsDisplayed()
        compose.onAllNodesWithText("Date unavailable").onLast().performScrollTo().assertIsDisplayed()
    }
    @Test fun emptyErrorAndRetryAreVisibleAndMismatchedScopeHidesOldEntries() {
        var ui by mutableStateOf(StatusUiState(scope, hasLoaded = true))
        var retried = false
        compose.setContent { MaterialTheme { StatusScreen(scope, ui, {}, { retried = true }) } }
        compose.onNodeWithText("No statuses yet. This address has a fresh start.").assertIsDisplayed()
        compose.runOnIdle { ui = ui.copy(error = "Offline") }
        compose.onNodeWithText("Showing last loaded statuses. Offline").assertIsDisplayed()
        compose.onNodeWithText("Retry statuses").performClick()
        compose.runOnIdle {
            assertTrue(retried)
            ui = StatusUiState(scope.copy(address = "other"), listOf(StatusEntry("old", "other", "", "Wrong address content", 1)), hasLoaded = true)
        }
        compose.onNodeWithText("Wrong address content").assertDoesNotExist()
        compose.onNodeWithText("Loading statuses…").assertIsDisplayed()
    }
}
