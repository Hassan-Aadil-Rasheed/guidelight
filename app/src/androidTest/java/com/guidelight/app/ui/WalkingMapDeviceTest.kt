package com.guidelight.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guidelight.app.navigation.GeoPoint
import com.guidelight.app.navigation.WalkingPosition
import com.guidelight.app.domain.WalkVoiceAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Sample coordinates test map rendering/state recovery, never the phone's GPS accuracy. */
@RunWith(AndroidJUnit4::class)
class WalkingMapDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val start = GeoPoint(28.5492, 77.2510)
    private fun walking() = GuideUiState(routeActive = true, routeName = "Nehru Place",
        instruction = "In about 80 metres, turn right.", remainingDistance = "650 m", routeDuration = "About 10 min",
        routeShape = listOf(start, GeoPoint(28.5500, 77.2510), GeoPoint(28.5500, 77.2560)),
        walkingPosition = WalkingPosition(start, 8f, false, 0.0), walkingPanel = WalkingPanel.MAP)

    @Test fun nativeMapLoadsAndPositionRecoversWithoutLosingRouteOrControls() {
        var state by mutableStateOf(walking())
        compose.setContent { GuideLightTheme(darkTheme = true) { GuideLightApp(state, GuideActions(onWalkAction = { action ->
            state = state.copy(mapOverview = action == WalkVoiceAction.FULL_ROUTE, mapCameraRequest = state.mapCameraRequest + 1)
        })) } }
        compose.waitUntil(45_000) {
            compose.onAllNodes(hasStateDescription("Map ready")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Live location").assertExists()
        compose.onNodeWithText("Full route").performScrollTo().performClick()
        compose.onNodeWithText("Recenter").performClick()
        compose.onNodeWithText("Following you").assertExists()
        compose.runOnIdle { state = state.copy(walkingPosition = WalkingPosition(start, 8f, true),
            turnGuidanceAvailable = false, instruction = "Updating location. Turn guidance paused.") }
        compose.onNodeWithText("Last known location").assertExists()
        compose.onNodeWithTag("walking-map").assertExists()
        compose.onNodeWithTag("instruction").assertTextEquals("Updating location. Turn guidance paused.")
        compose.runOnIdle { state = walking().copy(walkingPosition = WalkingPosition(GeoPoint(28.5494, 77.2510), 8f, false, 0.0)) }
        compose.onNodeWithText("Live location").assertExists()
        screenshot("live-walking-map")
        compose.runOnIdle { state = state.copy(walkingPosition = WalkingPosition(start, 90f, false),
            turnGuidanceAvailable = false, instruction = "Updating location. Turn guidance paused.") }
        compose.onNodeWithText("Approximate location").assertExists()
        screenshot("weak-signal-walking-map")
    }

    @Test fun largeTextKeepsMapAndWalkingActionsReachable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                GuideLightTheme { GuideLightApp(walking(), GuideActions()) }
            }
        }
        compose.onNodeWithText("Full route").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("walk-speak").assertIsDisplayed()
        compose.onNodeWithTag("walk-menu").assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Native GL draws asynchronously after Compose has committed its state.
        android.os.SystemClock.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
