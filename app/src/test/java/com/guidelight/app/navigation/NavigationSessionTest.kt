package com.guidelight.app.navigation

import org.junit.Assert.*
import org.junit.Test

class NavigationSessionTest {
    @Test fun turnAroundArrowDoesNotCreateAnInvalidGroundRibbon() {
        val result = NavigationSession(route()).update(shape.first(), 180.0)
        assertTrue(result.instruction.startsWith("Turn around"))
        assertEquals(180.0, kotlin.math.abs(result.arrowBearing!!), .01)
        assertNull(result.relativeBearing)
        assertTrue(result.groundPreview.isEmpty())
        assertNull(NavigationSession(route()).update(shape.first(), 180.0, accuracy = 90f).arrowBearing)
    }
    private fun point(north: Double, east: Double = 0.0) = GeoPoint(north / 111_195, east / 111_195)
    private val shape = listOf(point(0.0), point(50.0), point(100.0), point(150.0), point(200.0), point(200.0, 50.0), point(200.0, 100.0))
    private fun route() = WalkingRoute(listOf(
        RouteManeuver("Walk northwest on Example Road", shape.first(), false, 0, 1),
        RouteManeuver("Turn right onto Second Road", shape[4], false, 4, 7),
        RouteManeuver("You have arrived", shape.last(), true, 6, 4)
    ), .3, 270, shape)
    private fun sessionAt(metres: Double): NavigationSession = NavigationSession(route()).also { s ->
        for (m in 0..metres.toInt() step 25) s.update(point(m.toDouble()), 0.0, now = m * 1000L)
    }

    @Test fun startUsesRelativeDirectionInsteadOfCardinalText() {
        val s = NavigationSession(route())
        val result = s.update(shape.first(), 90.0)
        assertTrue(result.instruction.contains("Turn left to face the route"))
        assertFalse(result.instruction.contains("northwest"))
        assertNotNull(result.announcement)
    }
    @Test fun countdownUsesAlongRouteDistanceAndDoesNotAdvanceAt35Metres() {
        val s = sessionAt(150.0)
        assertTrue(s.update(point(150.0), 0.0, now = 160_000).instruction.contains("50 metres, turn right"))
        assertTrue(s.update(point(170.0), 0.0, now = 170_000).instruction.contains("30 metres, turn right"))
        assertTrue(s.update(point(190.0), 0.0, now = 190_000).instruction.contains("Turn right now"))
        assertTrue(s.update(point(210.0, 30.0), 90.0, now = 210_000).instruction.contains("destination"))
    }
    @Test fun badOrOldLocationSuppressesGroundGuide() {
        val s = NavigationSession(route())
        assertNull(s.update(shape.first(), 0.0, accuracy = 90f).relativeBearing)
        assertFalse(s.update(shape.first(), 0.0, fresh = false).guidanceAvailable)
    }
    @Test fun missingHeadingDoesNotInventARelativeDirection() {
        val result = NavigationSession(route()).update(shape.first())
        assertTrue(result.instruction.contains("Checking your direction"))
        assertTrue(result.groundPreview.isEmpty())
    }
    @Test fun weakSignalKeepsDistanceWithoutAdvancingOrShowingATurn() {
        val s = sessionAt(150.0)
        val before = s.update(point(150.0), 0.0, now = 160_000)
        val weak = s.update(point(200.0, 95.0), 0.0, accuracy = 90f, now = 170_000)
        assertEquals(before.remainingMetres, weak.remainingMetres, .01)
        assertFalse(weak.guidanceAvailable)
        assertFalse(weak.arrived)
        assertNull(weak.relativeBearing)
        assertTrue(weak.groundPreview.isEmpty())
        val recovered = s.update(point(170.0), 0.0, now = 180_000)
        assertTrue(recovered.guidanceAvailable)
        assertTrue(recovered.instruction.contains("30 metres, turn right"))
    }
    @Test fun weakSignalDoesNotRepeatEveryThirtySeconds() {
        val s = NavigationSession(route())
        assertNotNull(s.update(shape.first(), 0.0, accuracy = 90f, now = 0).announcement)
        assertNull(s.update(shape.first(), 0.0, accuracy = 90f, now = 30_000).announcement)
        assertNotNull(s.update(shape.first(), 0.0, now = 31_000).announcement)
    }
    @Test fun offRouteDoesNotCommandAnUnrecordedShortcut() {
        val result = NavigationSession(route()).update(point(0.0, 100.0), 0.0)
        assertTrue(result.instruction.contains("off the route"))
        assertNull(result.relativeBearing)
    }
    @Test fun countdownStagesSpeakOnceAndRepeatAfterThirtySeconds() {
        val s = NavigationSession(route())
        assertNotNull(s.update(shape.first(), 0.0, now = 0).announcement)
        assertNull(s.update(shape.first(), 0.0, now = 1000).announcement)
        assertNotNull(s.update(shape.first(), 0.0, now = 30_000).announcement)
    }
    @Test fun arrivalRequiresEndpointAndGoodAccuracy() {
        val s = sessionAt(200.0)
        s.update(point(200.0, 50.0), 90.0)
        assertFalse(s.update(point(200.0, 95.0), 90.0, accuracy = 24f).arrived)
        assertTrue(s.update(point(200.0, 95.0), 90.0, accuracy = 10f).arrived)
    }
    @Test fun previewFollowsCornerGeometryInsteadOfScreenCentre() {
        val s = sessionAt(175.0)
        val preview = s.update(point(198.0), 0.0).groundPreview
        assertTrue(preview.first().first < .05f)
        assertTrue(preview.last().first > 1f)
        assertTrue(preview.last().second < 2.1f)
    }
    @Test fun wraparoundAndRearLensAxesAreCorrect() {
        assertEquals(20.0, relativeAngle(10.0, 350.0), .001)
        assertEquals(-20.0, relativeAngle(350.0, 10.0), .001)
        assertEquals(0.0, rearCameraBearing(floatArrayOf(1f,0f,0f, 0f,0f,-1f, 0f,1f,0f))!!, .001)
        assertEquals(90.0, rearCameraBearing(floatArrayOf(0f,0f,-1f, -1f,0f,0f, 0f,1f,0f))!!, .001)
        assertNull(rearCameraBearing(floatArrayOf(1f,0f,0f, 0f,1f,0f, 0f,0f,1f)))
    }
}
