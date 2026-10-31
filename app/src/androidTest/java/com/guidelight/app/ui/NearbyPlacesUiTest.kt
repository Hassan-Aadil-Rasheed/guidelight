package com.guidelight.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guidelight.app.navigation.GeoPoint
import com.guidelight.app.navigation.NearbyCategory
import com.guidelight.app.navigation.PlaceResult
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NearbyPlacesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nearbySearchIsVoiceFirstWithoutCategoryButtons() {
        compose.setContent { GuideLightTheme { GuideLightApp(GuideUiState(), GuideActions()) } }
        compose.onNodeWithText("Find in Google Maps").assertDoesNotExist()
        compose.onNodeWithText("Find nearby").assertDoesNotExist()
        NearbyCategory.entries.forEach { category ->
            compose.onNodeWithTag("nearby-${category.name.lowercase()}").assertDoesNotExist()
        }
        compose.onNodeWithTag("speak").assertIsDisplayed()
        compose.onNodeWithTag("destination").assertExists()
    }

    @Test fun nearbyResultsExplainDistanceAndSelectExactStore() {
        val chemist = PlaceResult("Example Chemist", "Test Road", GeoPoint(28.5, 77.2), 120)
        var selected: PlaceResult? = null
        compose.setContent { GuideLightTheme { GuideLightApp(
            GuideUiState(destination = "Nearest chemist", nearbyCategory = NearbyCategory.CHEMIST, places = listOf(chemist)),
            GuideActions(onChoosePlace = { selected = it })) } }
        compose.onNodeWithText("Nearby chemists").assertExists()
        compose.onNodeWithText("Find in Google Maps").assertDoesNotExist()
        compose.onNodeWithText("Suggestions ordered by approximate distance. Walking distance may be longer.").assertExists()
        compose.onNodeWithTag("place-0").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(chemist, selected) }
    }
}
