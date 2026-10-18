package lol.omg.pocket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class PostingUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun demo() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Try demo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Try demo").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Example Person").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun localPostReceiptHistoryAndProfileSurviveRecreation() {
        demo(); compose.onNodeWithText("Statuslog").performClick()
        compose.onNodeWithText("New status").performScrollTo().performClick()
        compose.onNodeWithText("Status text").performTextInput("A local demo post")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("A local demo post").assertExists()
        compose.onNodeWithText("Post locally (demo)").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Saved locally in demo. Nothing was published online.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithTag("status-list").performScrollToIndex(3)
        compose.onNodeWithText("A local demo post").assertExists()
        compose.onNodeWithText("Profile").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Page content · Hide").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Read-only source viewer · Editing and publishing will come later.").assertExists()
    }
    @Test fun closingDraftRequiresDeliberateDiscard() {
        demo(); compose.onNodeWithText("Statuslog").performClick()
        compose.onNodeWithText("New status").performScrollTo().performClick()
        compose.onNodeWithText("Status text").performTextInput("Keep my words")
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.onNodeWithText("Discard this draft?").assertIsDisplayed()
        compose.onNodeWithText("Keep writing").performClick()
        compose.onNodeWithText("Keep my words").assertExists()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.onNodeWithText("Discard draft").performClick()
        compose.onNodeWithText("Status text").assertDoesNotExist()
    }
}
