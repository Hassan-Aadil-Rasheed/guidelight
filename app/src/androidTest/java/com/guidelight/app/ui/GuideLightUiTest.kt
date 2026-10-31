package com.guidelight.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guidelight.app.domain.GuidanceDirection
import com.guidelight.app.domain.ObstacleRisk
import com.guidelight.app.domain.PlaceChoiceVoice
import com.guidelight.app.domain.PlaceChoiceAction
import com.guidelight.app.navigation.GeoPoint
import com.guidelight.app.navigation.PlaceResult
import com.guidelight.app.remote.BridgePhase
import com.guidelight.app.remote.BridgeStatus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GuideLightUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun destinationEntryEnablesSearchAndClearDisablesIt() {
        var state by mutableStateOf(GuideUiState(locationReady = true))
        var searches = 0
        compose.setContent {
            GuideLightTheme(darkTheme = false) {
                GuideLightApp(state, GuideActions(onDestination = { state = state.copy(destination = it) }, onSearch = { searches++ }))
            }
        }
        compose.onNodeWithTag("search").assertIsNotEnabled()
        compose.onNodeWithTag("destination").performTextInput("India Gate")
        compose.onNodeWithTag("search").assertIsEnabled().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, searches) }
        compose.onNodeWithContentDescription("Clear destination").performClick()
        compose.onNodeWithTag("search").assertIsNotEnabled()
    }

    @Test fun voiceActionFocusesTheDestinationAndExplainsFlowBubble() {
        var state by mutableStateOf(GuideUiState(locationReady = true))
        compose.setContent { GuideLightTheme { GuideLightApp(state, GuideActions(onSpeak = { state = state.copy(voiceArmed = true, voiceRequest = 1) })) } }
        compose.onNodeWithTag("speak").performClick()
        compose.onNodeWithTag("destination").assertIsFocused()
        compose.onNodeWithText("Hold the Wispr Flow bubble, then speak.").assertExists()
    }

    @Test fun placeSelectionUsesTheExactChosenResult() {
        val park = PlaceResult("Lodhi Garden", "Lodhi Road, New Delhi", GeoPoint(28.593, 77.220), 850)
        var chosen: PlaceResult? = null
        compose.setContent { GuideLightTheme { GuideLightApp(GuideUiState(destination = "park", places = listOf(park)), GuideActions(onChoosePlace = { chosen = it })) } }
        compose.onNodeWithTag("place-0").performScrollTo().assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(park, chosen) }
    }

    @Test fun numberedSuggestionsAcceptWisprTextWithoutReplacingTheDestination() {
        val places = listOf(
            PlaceResult("City Park", "North entrance", GeoPoint(28.6, 77.2), 500),
            PlaceResult("City Park", "South entrance", GeoPoint(28.7, 77.3), 1200)
        )
        var state by mutableStateOf(GuideUiState(destination = "park", places = places))
        var chosen: PlaceResult? = null
        var repeats = 0
        compose.setContent { GuideLightTheme { GuideLightApp(state, GuideActions(
            onSpeak = { state = state.copy(voiceArmed = true, voiceRequest = state.voiceRequest + 1) },
            onChoiceInput = { state = state.copy(choiceInput = it) },
            onSubmitChoice = { chosen = (PlaceChoiceVoice.resolve(state.choiceInput, places) as? PlaceChoiceAction.Choose)?.place },
            onRepeatPlaces = { repeats++ }
        )) } }
        compose.onNodeWithTag("repeat-suggestions").performClick()
        compose.runOnIdle { assertEquals(1, repeats) }
        compose.onNodeWithTag("choose-voice").performClick()
        compose.onNodeWithTag("choice-input").assertIsFocused().performTextInput("Choose suggestion two.")
        compose.onNodeWithContentDescription("Use this choice").performClick()
        compose.runOnIdle {
            assertEquals(places[1], chosen)
            assertEquals("park", state.destination)
        }
        compose.onNodeWithTag("place-1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Suggestion 2").assertExists()
        screenshot("voice-suggestions")
    }

    @Test fun largeTextSuggestionsKeepVoiceRepeatAndEveryChoiceReachable() {
        val places = (1..5).map {
            PlaceResult("Park $it", "A long nearby address for suggestion $it", GeoPoint(28.6, 77.2), it * 500)
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                GuideLightTheme { GuideLightApp(GuideUiState(destination = "park", places = places), GuideActions()) }
            }
        }
        compose.onNodeWithTag("choose-voice").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("repeat-suggestions").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("choice-input").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("place-4").performScrollTo().assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Change destination").performScrollTo().assertIsDisplayed()
    }

    @Test fun destinationEditorSurvivesSearchAndBecomesTheChoiceEditor() {
        val park = PlaceResult("City Park", "North entrance", GeoPoint(28.6, 77.2), 500)
        var state by mutableStateOf(GuideUiState(destination = "park", voiceArmed = true, voiceRequest = 1))
        compose.setContent { GuideLightTheme { GuideLightApp(state, GuideActions()) } }
        compose.onNodeWithTag("destination").assertIsFocused()
        val editorId = compose.onNodeWithTag("destination").fetchSemanticsNode().id
        compose.runOnIdle { state = state.copy(busy = true, voiceArmed = false) }
        assertEquals(editorId, compose.onNodeWithTag("destination").fetchSemanticsNode().id)
        compose.runOnIdle { state = state.copy(busy = false, places = listOf(park)) }
        compose.onNodeWithTag("choice-input").assertIsFocused()
        assertEquals(editorId, compose.onNodeWithTag("choice-input").fetchSemanticsNode().id)
    }

    @Test fun emptyChoiceSubmissionCanBeRetriedFromTheVoiceButton() {
        val park = PlaceResult("City Park", "North entrance", GeoPoint(28.6, 77.2), 500)
        var retry = false
        compose.setContent { GuideLightTheme { GuideLightApp(
            GuideUiState(destination = "park", places = listOf(park), remote = BridgeStatus(true, BridgePhase.SUBMITTING)),
            GuideActions(onSpeak = { retry = true })
        ) } }
        compose.onNodeWithTag("choose-voice").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(retry) }
    }

    @Test fun walkingShowsDirectionWarningAndRequiresEndConfirmation() {
        var ended = false
        var state by mutableStateOf(GuideUiState(routeActive = true, cameraAllowed = true,
            routeName = "India Gate", instruction = "Turn left onto Kartavya Path", direction = GuidanceDirection.LEFT,
            routeDistance = "3.0 km", routeDuration = "About 48 min",
            lastWarning = "Stop. Possible obstacle ahead.", warningRisk = ObstacleRisk.STOP))
        compose.setContent { GuideLightTheme { GuideLightApp(state, GuideActions(onStop = { ended = true },
            onStopConfirmation = { state = state.copy(confirmEndWalk = it) },
            onWalkAction = { state = state.copy(walkingPanel = WalkingPanel.CONTROLS) })) } }
        compose.onNodeWithTag("instruction").assertTextEquals("Turn left onto Kartavya Path")
        compose.onNodeWithText("Stop. Possible obstacle ahead.").assertExists()
        screenshot("walking")
        compose.onNodeWithTag("walk-menu").performClick()
        compose.onNodeWithText("End walk").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(ended) }
        compose.onNodeWithText("End this walk?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Keep walking").performScrollTo().performClick()
        compose.onNodeWithText("End walk").performScrollTo().performClick()
        compose.onNodeWithTag("confirm-end-walk").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(ended) }
    }

    @Test fun walkCommandsHaveAFocusedEditorAndKeepTheRemoteAvailableDuringConfirmation() {
        var state by mutableStateOf(GuideUiState(routeActive = true, routeName = "Nehru Place", instruction = "Turn left", destination = "Nehru Place"))
        var ready = false
        var submitted = ""
        compose.setContent { GuideLightTheme { GuideLightApp(state, GuideActions(
            onDestinationScreenReady = { ready = it },
            onSpeak = { state = state.copy(voiceArmed = true, voiceRequest = state.voiceRequest + 1) },
            onWalkCommand = { state = state.copy(walkCommandInput = it) },
            onSubmitWalkCommand = { submitted = state.walkCommandInput }
        )) } }
        compose.runOnIdle { assertTrue(ready) }
        compose.onNodeWithTag("walk-speak").performClick()
        compose.onNodeWithTag("walk-command").assertIsFocused().performTextInput("Repeat directions.")
        val editorId = compose.onNodeWithTag("walk-command").fetchSemanticsNode().id
        compose.onNodeWithContentDescription("Send walk command").performClick()
        compose.runOnIdle {
            assertEquals("Repeat directions.", submitted)
            assertEquals("Nehru Place", state.destination)
            state = state.copy(confirmEndWalk = true)
        }
        assertEquals(editorId, compose.onNodeWithTag("walk-command").fetchSemanticsNode().id)
        compose.onNodeWithText("Keep walking").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(ready)
            state = state.copy(remote = BridgeStatus(true, BridgePhase.SUBMITTING))
        }
        compose.onNodeWithTag("walk-speak").assertIsEnabled()
        screenshot("walk-voice")
    }

    @Test fun largeTextKeepsWalkingVoiceAndConfirmationControlsReachable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                GuideLightTheme { GuideLightApp(GuideUiState(routeActive = true, routeName = "Nehru Place",
                    instruction = "Turn right", confirmEndWalk = true), GuideActions()) }
            }
        }
        compose.onNodeWithTag("walk-speak").assertIsDisplayed()
        compose.onNodeWithTag("confirm-end-walk").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Keep walking").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("walk-command").performScrollTo().assertIsDisplayed()
    }

    @Test fun largeTextKeepsPrimaryControlsReachable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                GuideLightTheme(darkTheme = false) { GuideLightApp(GuideUiState(destination = "India Gate", locationReady = true), GuideActions()) }
            }
        }
        compose.onNodeWithTag("speak").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("destination").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("search").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithContentDescription("Help and app information").assertIsDisplayed()
        screenshot("large-text")
    }

    @Test fun cameraTabAndHelpAreAccessibleWithoutStartingAWalk() {
        var state by mutableStateOf(GuideUiState(cameraAllowed = true))
        compose.setContent { GuideLightTheme(darkTheme = true) { GuideLightApp(state, GuideActions(onHelp = { state = state.copy(helpVisible = it) })) } }
        screenshot("destination-dark")
        compose.onNodeWithText("Camera", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("camera-heading").assertIsDisplayed()
        compose.onNodeWithText("Repeat alert").performScrollTo().assertIsNotEnabled()
        screenshot("camera-dark")
        compose.onNodeWithContentDescription("Help and app information").performClick()
        compose.onNodeWithText("Walking with GuideLight").assertIsDisplayed()
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("Walking with GuideLight").assertDoesNotExist()
    }

    @Test fun lightDestinationScreenshot() {
        compose.setContent { GuideLightTheme(darkTheme = false) { GuideLightApp(GuideUiState(locationReady = true), GuideActions()) } }
        screenshot("destination-light")
    }

    @Test fun remoteControlRequiresExplicitOptInAndShowsPermissionState() {
        var state by mutableStateOf(GuideUiState(remoteSetupVisible = true))
        var permissionOpened = false
        compose.setContent { GuideLightTheme {
            GuideLightApp(state, GuideActions(
                onRemoteEnabled = { state = state.copy(remoteEnabled = it) },
                onRemotePermission = { permissionOpened = true }
            ))
        } }
        compose.onNodeWithContentDescription("Use GuideLight button").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Button control is not running. Open Accessibility settings to enable or restart GuideLight button control.").assertExists()
        compose.onNodeWithText("Accessibility settings").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(permissionOpened) }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "ui-review/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
