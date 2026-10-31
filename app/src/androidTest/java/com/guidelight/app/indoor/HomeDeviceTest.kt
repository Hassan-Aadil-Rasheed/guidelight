package com.guidelight.app.indoor

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Hardware smoke check; physical room recording is intentionally left to an assisted home trial. */
@RunWith(AndroidJUnit4::class)
class HomeDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<HomeActivity>()
    @Test fun homeCameraAndWisprEditorRestartAndUnmappedRoomsDoNotStartWalks() {
        compose.onNodeWithTag("home-start").performScrollTo().performClick()
        waitForCamera()
        compose.runOnIdle { com.google.common.truth.Truth.assertThat(compose.activity.markerDatabaseSize).isEqualTo(2) }
        speak("Take me to codex unrecorded test room")
        compose.waitUntil(5000) { compose.onAllNodesWithText("codex unrecorded test room is not saved. Say list rooms to hear saved places.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home-end").assertDoesNotExist()
        // Do not inject save-room commands against an uncontrolled live camera: a physical
        // marker might actually be visible. Parser/save behaviour is covered with fixtures.
        val spokenBefore = compose.activity.speechCompletedCount.get()
        val framesBefore = compose.activity.cameraFrameCount.get()
        speak("List my rooms")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("home-instruction").fetchSemanticsNodes().any {
            it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
                .any { text -> text.text.startsWith("No rooms saved yet.") || text.text.startsWith("Saved rooms:") }
        } }
        compose.waitUntil(15000) { compose.activity.speechCompletedCount.get() > spokenBefore }
        compose.waitUntil(5000) { compose.activity.cameraFrameCount.get() > framesBefore + 3 }
        compose.onNodeWithTag("home-camera").assertIsDisplayed()
        com.google.common.truth.Truth.assertThat(compose.onNodeWithTag("home-command").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text).isEmpty()
        compose.onNodeWithText("Send command").assertDoesNotExist()
        compose.onNodeWithText("Stop camera").performScrollTo().performClick()
        compose.onNodeWithTag("home-command").assertDoesNotExist()
        compose.onNodeWithTag("home-start").performScrollTo().performClick()
        waitForCamera()
        compose.onNodeWithText("Stop camera").performScrollTo().performClick()
    }
    private fun speak(text: String) {
        compose.onNodeWithTag("home-speak").performScrollTo().performClick()
        compose.onNodeWithTag("home-command").assertIsFocused().performTextInput(text)
    }
    private fun waitForCamera() { compose.waitUntil(45000) { compose.onAllNodesWithText("Camera on · object scan on").fetchSemanticsNodes().isNotEmpty() } }
}
