package com.guidelight.app.navigation

import org.junit.Assert.*
import org.junit.Test

class LocationFixPolicyTest {
    private fun fix(accuracy: Float, at: Long = 10_000) = LocationFix(GeoPoint(28.5, 77.2), accuracy, at)

    @Test fun coarseNetworkFixCannotReplaceFreshPreciseGps() {
        assertFalse(acceptsLocationFix(fix(8f), fix(120f, 11_000), 11_000))
        assertTrue(acceptsLocationFix(fix(8f), fix(120f, 21_000), 21_000))
    }
    @Test fun preciseRecoveryReplacesApproximateLocationImmediately() {
        assertTrue(acceptsLocationFix(fix(120f), fix(8f, 10_100), 10_100))
    }
    @Test fun rejectsOutOfOrderInvalidAndAncientFixes() {
        assertFalse(acceptsLocationFix(fix(8f), fix(6f, 9_999), 11_000))
        assertFalse(acceptsLocationFix(null, fix(Float.NaN), 11_000))
        assertFalse(acceptsLocationFix(null, fix(0f), 11_000))
        assertFalse(acceptsLocationFix(null, fix(8f), 131_000))
        assertFalse(acceptsLocationFix(null, fix(8f, 12_000), 11_000))
        assertFalse(acceptsLocationFix(null, fix(8f).copy(point = GeoPoint(91.0, 77.2)), 11_000))
    }
    @Test fun equalTimestampCanOnlyImproveAccuracy() {
        assertTrue(acceptsLocationFix(fix(30f), fix(8f), 11_000))
        assertFalse(acceptsLocationFix(fix(8f), fix(30f), 11_000))
        assertFalse(acceptsLocationFix(fix(8f), fix(8f), 11_000))
    }
    @Test fun mapNeverLabelsStaleOrCoarsePositionAsLive() {
        assertEquals("Last known location", WalkingPosition(fix(8f).point, 8f, true).label)
        assertEquals("Approximate location", WalkingPosition(fix(80f).point, 80f, false).label)
        assertEquals("Live location", WalkingPosition(fix(8f).point, 8f, false).label)
    }
}
