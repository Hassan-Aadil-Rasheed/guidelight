package com.guidelight.app.indoor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guidelight.app.ui.GuideLightTheme
import com.guidelight.app.vision.LocalAiTask
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SavedHomeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun assistantActionsRemainReachableWithLargeTextAndPinnedCamera() {
        var selected: LocalAiTask? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Fixture(onAi = { selected = it })
            }
        }
        compose.onNodeWithTag("saved-map-camera").assertIsDisplayed()
        compose.onNodeWithTag("home-describe").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(LocalAiTask.DESCRIBE, selected) }
        compose.onNodeWithTag("home-identify").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(LocalAiTask.IDENTIFY_ROOM, selected) }
        compose.onNodeWithTag("home-more-options").performScrollTo().performClick()
        compose.onNodeWithText("Cloudflare settings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Detailed analysis (slow)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("End test").performScrollTo().assertIsDisplayed()
        screenshot("home-large-text")
    }

    @Test fun expandingSettingsAndChangingCameraStatePreserveTheVoiceEditor() {
        var voice by mutableStateOf(HomeVoiceState())
        var state by mutableStateOf(readyState())
        var submitted = ""
        compose.setContent {
            Fixture(state, voice,
                onSpeak = { voice = voice.copy(armed = true, request = voice.request + 1) },
                onText = { voice = voice.copy(text = it) }, onSubmit = { submitted = voice.text })
        }
        compose.onNodeWithTag("saved-map-speak").performClick()
        compose.onNodeWithTag("saved-map-command").performScrollTo().assertIsFocused().performTextInput("Describe the view")
        val editorId = compose.onNodeWithTag("saved-map-command").fetchSemanticsNode().id
        compose.onNodeWithTag("saved-map-camera").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send home command").performClick()
        compose.runOnIdle {
            assertEquals("Describe the view", submitted)
            voice = voice.copy(armed = false)
            state = state.copy(aiBusy = true)
        }
        compose.onNodeWithText("Cancel analysis").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state = state.copy(aiBusy = false, preview = false) }
        compose.onNodeWithTag("home-more-options").performScrollTo().performClick()
        compose.onNodeWithText("Cloudflare settings").performScrollTo().assertIsDisplayed()
        assertEquals(editorId, compose.onNodeWithTag("saved-map-command").fetchSemanticsNode().id)
        compose.onNodeWithTag("saved-map-command").assertIsFocused().assertTextContains("Describe the view")
    }

    @Test fun roomIdentificationIsDisabledUntilAHomeMapIsSaved() {
        compose.setContent { Fixture(state = readyState().copy(hasMap = false)) }
        compose.onNodeWithTag("home-describe").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("home-identify").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Save a home map to identify rooms.").assertExists()
    }

    @Test fun homePreview() {
        compose.setContent { Fixture() }
        screenshot("home-black-yellow")
    }

    @Composable
    private fun Fixture(
        state: SavedHomeState = readyState(), voice: HomeVoiceState = HomeVoiceState(),
        onSpeak: () -> Unit = {}, onText: (String) -> Unit = {}, onSubmit: () -> Unit = {},
        onAi: (LocalAiTask) -> Unit = {}
    ) {
        GuideLightTheme {
            SavedHomeScreen(state, voice, onBack = {}, onCreateMap = {}, onFind = {}, onSave = {}, onStop = {},
                onSpeak = onSpeak, onText = onText, onSubmit = onSubmit, onLegacy = {}, onAi = onAi,
                onAiSetup = {}, onAiCancel = {}, onCloudSetup = {}, camera = { modifier ->
                    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CenterFocusStrong, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Camera preview", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                })
        }
    }

    private fun readyState() = SavedHomeState(preview = true, aiReady = true, cloudReady = true, hasMap = true,
        cameraStatus = "Camera tracking ready", aiMessage = "Cloudflare is ready", stage = SavedHomeStage.RECOGNIZED,
        message = "Your saved home map is ready.")

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "ui-review/$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
