package com.guidelight.app.indoor

import org.junit.Assert.*
import org.junit.Test

class MarkerMeasurementTest {
    @Test fun noDistanceUntilMarkerIsScanned() {
        assertNull(MarkerMeasurement().displacement(floatArrayOf(1f, 2f, 3f)))
    }

    @Test fun measuresDisplacementFromScanRatherThanDistanceToMarker() {
        val measurement = MarkerMeasurement()
        measurement.begin(floatArrayOf(2f, 1f, 3f))
        assertEquals(0f, measurement.displacement(floatArrayOf(2f, 1f, 3f))!!, .001f)
        assertEquals(5f, measurement.displacement(floatArrayOf(5f, 5f, 3f))!!, .001f)
        assertEquals(0f, measurement.displacement(floatArrayOf(2f, 1f, 3f))!!, .001f)
    }

    @Test fun trackingLossClearsStaleMeasurementAndRescanStartsFresh() {
        val measurement = MarkerMeasurement()
        measurement.begin(floatArrayOf(0f, 0f, 0f))
        measurement.clear()
        assertNull(measurement.displacement(floatArrayOf(10f, 0f, 0f)))
        measurement.begin(floatArrayOf(10f, 0f, 0f))
        assertEquals(1f, measurement.displacement(floatArrayOf(11f, 0f, 0f))!!, .001f)
    }

    @Test fun invalidPoseIsNeverDisplayedAsDistance() {
        val measurement = MarkerMeasurement()
        measurement.begin(floatArrayOf(0f, 0f, 0f))
        assertNull(measurement.displacement(floatArrayOf(Float.NaN, 0f, 0f)))
        assertNull(measurement.displacement(floatArrayOf(0f)))
    }

    @Test fun retainsCopyOfInitialPose() {
        val measurement = MarkerMeasurement()
        val pose = floatArrayOf(0f, 0f, 0f)
        measurement.begin(pose)
        pose[0] = 99f
        assertEquals(1f, measurement.displacement(floatArrayOf(1f, 0f, 0f))!!, .001f)
    }
}
