package com.guidelight.app.indoor

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SavedHomeDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun actualNativeEngineStreamsAndRecordsToAnIsolatedDraft() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "map-test-${UUID.randomUUID()}"
        val fixture = File(context.cacheDir, name)
        val scenario = ActivityScenario.launch<SavedHomeActivity>(Intent(context, SavedHomeActivity::class.java)
            .putExtra("isolatedTestDirectory", name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            compose.onNodeWithTag("saved-map-record").performScrollTo().performClick()
            compose.waitUntil(60_000) {
                compose.onAllNodesWithTag("saved-map-views").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitUntil(45_000) {
                var frames = 0; var stats = 0; var nodes = 0
                scenario.onActivity { frames = it.renderedFrames.get(); stats = it.nativeStats.get(); nodes = it.recordedNodeCount.get() }
                frames > 10 && stats > 1 && nodes > 0
            }
            compose.onNodeWithTag("saved-map-camera").assertIsDisplayed()
            assertThat(SavedHomeStore(fixture).current()).isNull()
            compose.onNodeWithText("End test").performScrollTo().performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("saved-map-record").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            scenario.close()
            // Only this explicitly named, internally created test directory is removed.
            check(fixture.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator))
            fixture.deleteRecursively()
        }
    }
}
