package com.guidelight.app.indoor

import com.google.ar.core.Pose
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class HomeCoordinatesTest {
    @Test fun markerRelativeCoordinatesSurviveADifferentArSessionOriginAndYaw() {
        val identity = Pose(floatArrayOf(1f, 2f, 3f), floatArrayOf(0f, 0f, 0f, 1f))
        val camera = Pose.makeTranslation(2f, 3f, 5f)
        val expected = HomeCoordinates.point(identity, camera)!!
        val half = sqrt(.5f)
        val transform = Pose(floatArrayOf(10f, 5f, -7f), floatArrayOf(0f, half, 0f, half))
        val actual = HomeCoordinates.point(transform.compose(identity), transform.compose(camera))!!
        assertEquals(expected.x, actual.x, .001f)
        assertEquals(expected.y, actual.y, .001f)
        assertEquals(expected.z, actual.z, .001f)
    }
}
