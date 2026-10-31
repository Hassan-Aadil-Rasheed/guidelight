package com.guidelight.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guidelight.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses the phone's real location and user-approved category provider. */
@RunWith(AndroidJUnit4::class)
class NearbyPlacesDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun worshipAndDailyNeedsAreSearchedByVoiceWithNoCategoryButtons() {
        val queries = listOf("Nearest masjid" to "Nearby mosques", "Nearest temple" to "Nearby temples",
            "Nearest church" to "Nearby churches", "Nearest public toilet" to "Nearby public toilets")
        queries.forEachIndexed { index, (query, title) ->
            compose.onNodeWithTag("speak").performScrollTo().performClick()
            compose.onNodeWithTag("destination").performTextInput(query)
            compose.waitUntil(90_000) {
                compose.onAllNodesWithTag("place-0").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("No suggestions found within", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("Nearby search is unavailable", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            if (compose.onAllNodesWithTag("place-0").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithText(title).assertExists()
                compose.onNodeWithTag("place-0").performScrollTo()
                screenshot("voice-nearby-$index")
                compose.onNodeWithText("Change destination").performScrollTo().performClick()
            } else {
                screenshot("voice-nearby-no-data-$index")
                org.junit.Assert.fail("No mapped suggestions returned for $query; inspect the captured provider result")
            }
        }
    }

    @Test fun nearestChemistTextFindsNearbyOptionsAndStartsWalkingRoute() {
        compose.onNodeWithTag("destination").performScrollTo().performTextInput("Could you find me the closest medical shop near here?")
        compose.onNodeWithTag("search").performScrollTo().performClick()
        awaitResults("Nearby chemists")
        compose.onNodeWithTag("place-0").performScrollTo().assertIsDisplayed()
        screenshot("nearby-chemists")
        compose.onNodeWithTag("place-0").performClick()
        compose.waitUntil(45_000) { compose.onAllNodesWithTag("walking-camera-screen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("instruction").assertExists()
        compose.onNodeWithTag("walking-camera-preview").assertExists()
        compose.onNodeWithTag("walking-map").assertDoesNotExist()
        screenshot("nearby-chemist-route")
        compose.onNodeWithTag("walk-menu").performClick()
        compose.onNodeWithText("End walk").performScrollTo().performClick()
        compose.onNodeWithTag("confirm-end-walk").performScrollTo().performClick()
        compose.onNodeWithTag("destination").assertExists()
    }

    @Test fun shopsVoiceTextFindsNearbyOptions() {
        compose.onNodeWithText("Where to?").performScrollTo()
        screenshot("nearby-categories")
        compose.onNodeWithTag("speak").performScrollTo().performClick()
        compose.onNodeWithTag("destination").performTextInput("Nearest shop")
        awaitResults("Nearby shops")
        compose.onNodeWithTag("place-0").performScrollTo().assertIsDisplayed()
        screenshot("nearby-shops")
    }

    private fun awaitResults(title: String) {
        compose.waitUntil(90_000) {
            compose.onAllNodesWithTag("place-0").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Nearby search is unavailable", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("suggestions found within 5 km", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Location unavailable", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithTag("place-0").fetchSemanticsNodes().isEmpty()) screenshot("nearby-search-failure")
        compose.onNodeWithTag("place-0").assertExists()
        compose.onNodeWithText(title).assertExists()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
