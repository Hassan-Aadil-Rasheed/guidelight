package com.guidelight.app.ui

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.inspector.WindowInspector
import android.widget.EditText
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.guidelight.app.MainActivity
import com.guidelight.app.R
import com.guidelight.app.indoor.SavedHomeActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class VoiceAccessDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun spokenHomeNavigationOpensHomeAndSpokenBackReturnsWithoutTheKeyboard() {
        val scenario = ActivityScenario.launch<MainActivity>(Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java))
        try {
            compose.onNodeWithTag("speak").performClick()
            compose.onNodeWithTag("destination").performTextInput("Home navigation")
            compose.waitUntil(10_000) { resumedActivity() is SavedHomeActivity }
            compose.waitUntil(5_000) { !keyboardVisible() }
            compose.waitUntil(30_000) { runCatching { compose.onNodeWithTag("saved-map-speak").assertIsDisplayed(); true }.getOrDefault(false) }
            compose.onNodeWithTag("saved-map-speak").performClick()
            compose.onNodeWithTag("saved-map-command").assertIsFocused()
            assertHomeHasNoCommandPopup()
            compose.onNodeWithTag("saved-map-command").performTextInput("Back to navigation")
            compose.waitUntil(10_000) { resumedActivity() is MainActivity }
            compose.onNodeWithTag("indoor-test").assertIsDisplayed()
            compose.waitUntil(5_000) { !keyboardVisible() }
        } finally { closeActivities(); scenario.close() }
    }

    @Test fun spokenHelpClosesTheKeyboardAndDoesNotSearchForTheCommandAsAPlace() {
        val scenario = ActivityScenario.launch<MainActivity>(Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java))
        try {
            listOf("What can I say", "Location status", "Camera status", "Repeat alert").forEach { command ->
                compose.onNodeWithTag("speak").performClick()
                compose.waitUntil(5_000) { keyboardVisible() }
                compose.onNodeWithTag("destination").performTextInput(command)
                compose.waitUntil(5_000) { !keyboardVisible() }
                compose.waitUntil(5_000) { compose.onAllNodesWithTag("indoor-test").fetchSemanticsNodes().any { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() } }
                compose.onNodeWithTag("destination").assertIsFocused()
                compose.onNodeWithTag("search").assertIsNotEnabled()
            }
        } finally { closeActivities(); scenario.close() }
    }

    @Test fun settingsDialogAcceptsAClosingCommandAndDismissesItsKeyboard() {
        val scenario = ActivityScenario.launch<MainActivity>(Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java))
        try {
            compose.onNodeWithContentDescription("Help and app information").performClick()
            compose.onNodeWithText("GuideLight button setup").performScrollTo().performClick()
            compose.onNodeWithTag("setup-speak").performScrollTo().performClick()
            insertDictation("Close settings")
            compose.waitUntil(8_000) { compose.onAllNodesWithTag("setup-speak").fetchSemanticsNodes().isEmpty() }
            compose.waitUntil(5_000) { !keyboardVisible() }
            compose.onNodeWithTag("indoor-test").assertIsDisplayed()
        } finally { closeActivities(); scenario.close() }
    }

    @Test fun homeUsesItsInlineCommandBoxAndClosesTheKeyboardAfterSuccessiveCommands() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "map-test-${UUID.randomUUID()}"
        val fixture = File(context.cacheDir, name)
        val scenario = ActivityScenario.launch<SavedHomeActivity>(Intent(context, SavedHomeActivity::class.java)
            .putExtra("isolatedTestDirectory", name))
        try {
            listOf("What can I say", "List my rooms").forEach { command ->
                compose.onNodeWithTag("saved-map-speak").performClick()
                compose.onNodeWithTag("saved-map-command").assertIsFocused()
                assertHomeHasNoCommandPopup()
                compose.waitUntil(5_000) { keyboardVisible() }
                compose.onNodeWithTag("saved-map-command").performTextInput(command)
                compose.waitUntil(5_000) { !keyboardVisible() }
                compose.waitUntil(5_000) {
                    compose.onAllNodesWithTag("saved-map-command").fetchSemanticsNodes().firstOrNull()
                        ?.config?.get(androidx.compose.ui.semantics.SemanticsProperties.EditableText)?.text?.isEmpty() == true
                }
                SystemClock.sleep(750)
                assertFalse("Home restored the keyboard after completing dictation", keyboardVisible())
                compose.onNodeWithTag("saved-map-command").assertIsNotFocused()
                assertHomeHasNoCommandPopup()
            }
            compose.onNodeWithTag("saved-map-record").performScrollTo().assertIsDisplayed()
            // Explicit manual typing must still work after suppressing automatic restoration.
            compose.onNodeWithTag("saved-map-command").performClick().performTextInput("x")
            compose.waitUntil(5_000) { keyboardVisible() }
        } finally {
            closeActivities(); scenario.close()
            check(fixture.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator))
            fixture.deleteRecursively()
        }
    }

    private fun resumedActivity(): Activity? {
        var result: Activity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return result
    }

    private fun assertHomeHasNoCommandPopup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertFalse("Home opened a second command editor in a popup",
                WindowInspector.getGlobalWindowViews().any {
                    it.findViewById<EditText>(R.id.saved_home_command) != null
                })
        }
    }

    private fun keyboardVisible(): Boolean {
        var visible = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            visible = WindowInspector.getGlobalWindowViews().filter { it.hasWindowFocus() }.any {
                ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
        }
        return visible
    }

    private fun insertDictation(text: String) {
        compose.waitUntil(5_000) {
            var ready = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ready = WindowInspector.getGlobalWindowViews().filter { it.hasWindowFocus() }
                    .any { it.findViewById<EditText>(R.id.saved_home_command) != null }
            }
            ready
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val editor = WindowInspector.getGlobalWindowViews().filter { it.hasWindowFocus() }
                .mapNotNull { it.findViewById<EditText>(R.id.saved_home_command) }.firstOrNull()
            assertNotNull("The app's focused Wispr editor was not present", editor)
            editor!!.setText(text)
        }
    }

    private fun closeActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach(Activity::finish)
        }
    }
}
