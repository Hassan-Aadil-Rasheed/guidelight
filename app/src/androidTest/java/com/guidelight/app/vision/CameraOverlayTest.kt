package com.guidelight.app.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.guidelight.app.domain.GuidanceDirection
import com.guidelight.app.domain.NormalizedBox
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraOverlayTest {
    @Test fun minimalWalkingModeMarksHazardCornersWithoutFullBoxesOrNeutralOutlines() {
        onOverlay { overlay ->
            overlay.minimalDetections = true
            overlay.setDetections(listOf(hazard, NormalizedBox(.025f, .1f, .2f, .35f)))
            val image = draw(overlay)
            assertThat(Color.alpha(image.getPixel(100, 190))).isGreaterThan(0)
            assertThat(Color.alpha(image.getPixel(100, 375))).isEqualTo(0)
            assertThat(Color.alpha(image.getPixel(10, 135))).isEqualTo(0)
        }
    }
    @Test fun groundModeNeverDrawsAFixedBeamWhenTrackingIsMissing() {
        onOverlay { overlay ->
            overlay.useGroundProjection = true
            overlay.setRoute(true, GuidanceDirection.STRAIGHT)
            assertThat(Color.alpha(draw(overlay).getPixel(200, 550))).isEqualTo(0)
            overlay.setGroundRibbon(GroundRibbon(listOf(.5f to .95f, .5f to .4f),
                listOf(.4f to .95f, .45f to .4f, .55f to .4f, .6f to .95f)))
            assertThat(Color.alpha(draw(overlay).getPixel(200, 550))).isGreaterThan(0)
            overlay.setGroundRibbon(null)
            assertThat(Color.alpha(draw(overlay).getPixel(200, 550))).isEqualTo(0)
        }
    }
    private val hazard = NormalizedBox(0.25f, 0.3f, 0.75f, 0.95f)

    @Test fun internalFocusGridIsNeverDrawn() {
        onOverlay { overlay ->
            val bitmap = draw(overlay)
            for (x in listOf(133, 266)) for (y in listOf(100, 200, 300, 400, 500)) {
                assertThat(Color.alpha(bitmap.getPixel(x, y))).isEqualTo(0)
            }
        }
    }

    @Test fun sideObjectsTouchingTheCentreNeverGetHazardColours() {
        onOverlay { overlay ->
            overlay.setDetections(listOf(NormalizedBox(.4f, .2f, .99f, .99f)))
            val edge = draw(overlay).getPixel(160, 300)
            assertThat(Color.alpha(edge)).isGreaterThan(0)
            assertThat(Color.blue(edge)).isGreaterThan(200)
            overlay.minimalDetections = true
            assertThat(Color.alpha(draw(overlay).getPixel(160, 300))).isEqualTo(0)
        }
    }

    @Test fun cameraTabShowsHazardBoxesWithoutAnActiveWalk() {
        onOverlay { overlay ->
            overlay.setDetections(listOf(hazard))
            val bitmap = draw(overlay)
            val edge = bitmap.getPixel(100, 375)
            assertThat(Color.alpha(edge)).isGreaterThan(0)
            assertThat(Color.red(edge)).isGreaterThan(Color.green(edge))
            // Detection is visible; the route line stays absent without a walk.
            assertThat(Color.alpha(bitmap.getPixel(200, 550))).isEqualTo(0)
        }
    }
    @Test fun detectedObjectsOutsideTheCorridorHaveNeutralOutlines() {
        onOverlay { overlay ->
            overlay.setDetections(listOf(NormalizedBox(0.025f, 0.1f, 0.2f, 0.35f)))
            val edge = draw(overlay).getPixel(10, 135)
            assertThat(Color.alpha(edge)).isGreaterThan(0)
            assertThat(Color.red(edge)).isGreaterThan(200)
            assertThat(Color.green(edge)).isGreaterThan(200)
            assertThat(Color.blue(edge)).isGreaterThan(200)
        }
    }
    @Test fun clearingDetectionsRemovesOldBoxes() {
        onOverlay { overlay ->
            overlay.setDetections(listOf(hazard))
            assertThat(Color.alpha(draw(overlay).getPixel(100, 375))).isGreaterThan(0)
            overlay.setDetections(emptyList())
            assertThat(Color.alpha(draw(overlay).getPixel(100, 375))).isEqualTo(0)
        }
    }
    @Test fun anActiveWalkStillDrawsItsBlueCorridor() {
        onOverlay { overlay ->
            overlay.setRoute(true, GuidanceDirection.STRAIGHT)
            val pixel = draw(overlay).getPixel(200, 550)
            assertThat(Color.alpha(pixel)).isGreaterThan(0)
            assertThat(Color.blue(pixel)).isGreaterThan(Color.red(pixel))
        }
    }

    private fun onOverlay(check: (PathGuidanceOverlay) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val overlay = PathGuidanceOverlay(instrumentation.targetContext)
            overlay.layout(0, 0, 400, 600)
            overlay.setRoute(false, GuidanceDirection.STRAIGHT)
            check(overlay)
        }
    }

    private fun draw(overlay: PathGuidanceOverlay): Bitmap =
        Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888).also { overlay.draw(Canvas(it)) }
}
