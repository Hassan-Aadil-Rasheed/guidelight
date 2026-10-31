package com.guidelight.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guidelight.app.MainActivity
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in connected checks: emulator GPS at Delhi, permissions granted, internet required. */
@RunWith(AndroidJUnit4::class)
class DeviceIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun scanVoiceRequestOpensCameraAndReturnsToNavigationWithoutTouchingTabs() {
        compose.onNodeWithTag("speak").performScrollTo().performClick()
        compose.onNodeWithTag("destination").performTextInput("Scan surroundings")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("camera-heading").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(45_000) { compose.onAllNodesWithText("Camera scanning").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(35_000) { cameraSpeechCompleted() }
        screenshot("voice-camera-alerts")
        compose.onNodeWithTag("camera-speak").performScrollTo().performClick()
        compose.onNodeWithTag("walk-command").assertIsFocused().performTextInput("Back to navigation")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("destination").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Find nearby").assertDoesNotExist()
    }

    @Test fun actualCameraStreamsAndReleasesAcrossTabs() {
        compose.onNodeWithText("Camera", useUnmergedTree = true).performClick()
        compose.waitUntil(45_000) {
            compose.onAllNodesWithText("Camera scanning").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Navigate", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("speak").assertIsDisplayed()
        compose.onNodeWithText("Camera", useUnmergedTree = true).performClick()
        compose.waitUntil(45_000) {
            compose.onAllNodesWithText("Camera scanning").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun liveDestinationSearchAndWalkingRoute() {
        compose.onNodeWithTag("destination").performScrollTo().performTextInput("Could you show me walking directions to India Gate, please?")
        compose.onNodeWithTag("search").performScrollTo().performClick()
        compose.waitUntil(50_000) {
            compose.onAllNodesWithTag("place-0").fetchSemanticsNodes().isNotEmpty()
        }
        // Simulate the ordinary text Wispr inserts; exercise the real activity's
        // debounce, selection parsing and route request rather than a UI callback stub.
        compose.onNodeWithTag("choose-voice").performClick()
        compose.onNodeWithTag("choice-input").assertIsFocused().performTextInput("Could you take the first one?")
        compose.waitUntil(40_000) {
            compose.onAllNodesWithTag("instruction").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("instruction").assertExists()
        compose.waitUntil(45_000) {
            compose.onAllNodesWithText("Waiting for your direction").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Point camera at ground ahead").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Route guide · ground detected").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Appears ", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // The real TTS engine must actually start AND complete a camera utterance
        // while navigation updates continue; a visible detection box is insufficient.
        compose.waitUntil(35_000) { cameraSpeechCompleted() }
        screenshot("camera-walking-live")
        speakWalkCommand("Could you open the options?")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("walking-panel").fetchSemanticsNodes().isNotEmpty() }
        screenshot("camera-walking-controls")
        speakWalkCommand("Bring up the map.")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("walking-map").fetchSemanticsNodes().isNotEmpty() }
        speakWalkCommand("Show the whole journey.")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Recenter").fetchSemanticsNodes().isNotEmpty() }
        speakWalkCommand("Back to the camera.")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("walking-panel").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("walking-camera-preview").assertExists()
        speakWalkCommand("Look ahead.")
        waitForVoiceSubmission()
        speakWalkCommand("How much further?")
        waitForVoiceSubmission()
        speakWalkCommand("Is the camera working?")
        waitForVoiceSubmission()
        speakWalkCommand("What commands are available?")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Voice help").fetchSemanticsNodes().isNotEmpty() }
        speakWalkCommand("Dismiss the menu.")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("walking-panel").fetchSemanticsNodes().isEmpty() }
        speakWalkCommand("Say that again.")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Use Wispr Flow to speak your command.").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("instruction").assertExists()
        speakWalkCommand("I’m done walking.")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("speak").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("confirm-end-walk").assertDoesNotExist()
        compose.onNodeWithTag("instruction").assertDoesNotExist()
        compose.onNodeWithTag("speak").performScrollTo().assertIsDisplayed()
    }

    private fun speakWalkCommand(text: String) {
        compose.onNodeWithTag("walk-speak").performClick()
        compose.onNodeWithTag("walk-command").assertIsFocused().performTextInput(text)
    }

    private fun waitForVoiceSubmission() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Use Wispr Flow to speak your command.").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(700)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun cameraSpeechCompleted(): Boolean {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val descriptor = automation.executeShellCommand("logcat -d --pid=${android.os.Process.myPid()} -s GuideSpeech:I")
        val log = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        return log.contains("Camera speech started") && log.contains("Camera speech completed")
    }
}
