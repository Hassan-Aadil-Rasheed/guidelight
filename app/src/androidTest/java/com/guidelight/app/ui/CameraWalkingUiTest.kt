package com.guidelight.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guidelight.app.domain.WalkVoiceAction
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraWalkingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cameraFillsWalkingScreenAndToolsRemainBehindTheCornerMenu() {
        var state by mutableStateOf(GuideUiState(routeActive = true, cameraAllowed = true,
            routeName = "City Park", instruction = "Turn left now", cameraStatus = "Object scanning active"))
        compose.setContent { GuideLightTheme { GuideLightApp(state, GuideActions(onWalkAction = { action ->
            state = state.copy(walkingPanel = if (action == WalkVoiceAction.OPEN_CONTROLS) WalkingPanel.CONTROLS else WalkingPanel.CAMERA)
        })) } }
        val screen = compose.onNodeWithTag("walking-camera-screen").fetchSemanticsNode().boundsInRoot
        val preview = compose.onNodeWithTag("walking-camera-preview").fetchSemanticsNode().boundsInRoot
        assertEquals(screen.height, preview.height, 1f)
        assertEquals(screen.width, preview.width, 1f)
        compose.onNodeWithTag("walking-map").assertDoesNotExist()
        compose.onNodeWithText("End walk").assertDoesNotExist()
        compose.onNodeWithTag("walk-menu").performClick()
        compose.onNodeWithText("Scan surroundings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Repeat alert").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Show map").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("End walk").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close walking panel").performScrollTo().performClick()
        compose.onNodeWithTag("walking-panel").assertDoesNotExist()
    }

    @Test fun wisprEditorKeepsIdentityAndFocusAcrossMenuAndMapChanges() {
        var state by mutableStateOf(GuideUiState(routeActive = true, cameraAllowed = true, routeName = "City Park"))
        compose.setContent { GuideLightTheme { GuideLightApp(state, GuideActions(
            onSpeak = { state = state.copy(voiceArmed = true, voiceRequest = state.voiceRequest + 1) },
            onWalkCommand = { state = state.copy(walkCommandInput = it) })) } }
        compose.onNodeWithTag("walk-speak").performClick()
        compose.onNodeWithTag("walk-command").assertIsFocused().performTextInput("Show map")
        val editor = compose.onNodeWithTag("walk-command").fetchSemanticsNode().id
        compose.runOnIdle { state = state.copy(walkingPanel = WalkingPanel.MAP) }
        compose.onNodeWithTag("walk-command").assertIsFocused().assertTextContains("Show map")
        assertEquals(editor, compose.onNodeWithTag("walk-command").fetchSemanticsNode().id)
        compose.runOnIdle { state = state.copy(walkingPanel = WalkingPanel.HELP) }
        assertEquals(editor, compose.onNodeWithTag("walk-command").fetchSemanticsNode().id)
    }
}
