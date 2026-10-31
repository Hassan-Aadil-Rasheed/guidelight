package com.guidelight.app.indoor

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.guidelight.app.ui.GuideLightTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun wisprEditorKeepsItsTextAndFocusAcrossRecordingAndWalking() {
        var state by mutableStateOf(HomeUiState(cameraRunning = true, loaded = true, tracked = true))
        var voice by mutableStateOf(HomeVoiceState())
        var ended = false
        compose.setContent {
            GuideLightTheme {
                HomeScreen(state, voice, onBack = {}, onStart = {}, onStopCamera = {},
                    onSpeak = { voice = voice.copy(armed = true, request = voice.request + 1) },
                    onText = { voice = voice.copy(text = it) }, onSubmit = {},
                    onChoose = {}, onList = {}, onRepeat = {}, onEnd = { ended = true },
                    camera = { modifier -> Box(modifier) { Text("Camera preview placeholder") } })
            }
        }
        compose.onNodeWithTag("home-speak").performScrollTo().performClick()
        compose.onNodeWithTag("home-command").assertIsFocused().performTextInput("Repeat directions")
        compose.onNodeWithTag("home-camera").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(mode = HomeMode.RECORDING) }
        compose.onNodeWithTag("home-command").assertIsFocused().assertTextContains("Repeat directions")
        compose.runOnIdle { state = state.copy(mode = HomeMode.WALKING, destination = "kitchen", remaining = 3f) }
        compose.onNodeWithTag("home-command").assertIsFocused().assertTextContains("Repeat directions")
        compose.onNodeWithTag("home-end").performScrollTo().performClick()
        compose.runOnIdle { assertThat(ended).isTrue() }
    }
}
