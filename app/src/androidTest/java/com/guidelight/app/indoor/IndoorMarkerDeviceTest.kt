package com.guidelight.app.indoor

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real ARCore/camera check. Does not claim marker recognition without the physical reference. */
@RunWith(AndroidJUnit4::class)
class IndoorMarkerDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MarkerTestActivity>()

    @Test fun arCameraStreamsAndCanRestartWithoutStaleDistance() {
        compose.onNodeWithTag("start-marker-test").performScrollTo().performClick()
        waitForFrame()
        compose.onNodeWithTag("marker-status").assertIsDisplayed()
        compose.onNodeWithText("Distance from scan position · estimate").assertDoesNotExist()
        compose.onNodeWithTag("stop-marker-test").performScrollTo().performClick()
        compose.onNodeWithTag("marker-camera").assertDoesNotExist()
        compose.onNodeWithTag("start-marker-test").performScrollTo().performClick()
        waitForFrame()
        compose.onNodeWithText("Distance from scan position · estimate").assertDoesNotExist()
        compose.onNodeWithTag("stop-marker-test").performScrollTo().performClick()
    }

    private fun waitForFrame() {
        compose.waitUntil(45_000) {
            compose.onAllNodesWithTag("ar-camera-ready").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
