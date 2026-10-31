package com.guidelight.app.domain

import org.junit.Assert.*
import org.junit.Test

class ObjectDescriptionTest {
    @Test fun describesTwoAxesAndApparentImageSize() {
        val small = ObjectAppearance.from(NormalizedBox(.75f, .05f, .9f, .2f))
        assertEquals(ObjectAppearance(ObstaclePosition.RIGHT, ObjectHeight.HIGH, ApparentSize.SMALL), small)
        assertEquals("Object on your right, high in the camera view. Appears small.", small.description())
        assertEquals(ObjectHeight.LOW, ObjectAppearance.from(NormalizedBox(.05f, .7f, .3f, 1f)).height)
        assertEquals(ApparentSize.LARGE, ObjectAppearance.from(NormalizedBox(.1f, .1f, .9f, .9f)).size)
    }
    @Test fun emptyOrInvalidDetectionsNeverClaimThePathIsClear() {
        assertTrue(ObjectDescriptions.observations(listOf(NormalizedBox(Float.NaN, 0f, .5f, 1f), NormalizedBox(.5f, .5f, .4f, .4f))).isEmpty())
        assertEquals("No objects detected in this view. Objects may be missed.", ObjectDescriptions.summary(emptyList()))
    }
    @Test fun sideObjectsAreDescribedWithoutBeingDeclaredPathHazards() {
        val box = NormalizedBox(.8f, .6f, 1f, .95f)
        assertEquals(ObstacleRisk.NONE, ObstacleEvaluator.evaluate(listOf(box)).risk)
        assertEquals(ObstaclePosition.RIGHT, ObjectDescriptions.observations(listOf(box)).single().side)
    }
    @Test fun hazardAddsAppearanceWithoutInventingDepthOrMeasurements() {
        val alert = ObstacleEvaluator.evaluate(listOf(NormalizedBox(.1f, .4f, .65f, 1f)))
        assertEquals(ObstacleRisk.STOP, alert.risk)
        val spoken = ObjectDescriptions.warning(alert)
        assertTrue(spoken.startsWith("Stop."))
        assertTrue(spoken.contains("Appears large."))
        assertFalse(spoken.contains("metres"))
    }
    @Test fun announcementsRequireStabilityRespectDictationAndThrottleRepeats() {
        val gate = ObjectAnnouncementGate()
        val objects = listOf(ObjectAppearance(ObstaclePosition.LEFT, ObjectHeight.LOW, ApparentSize.MEDIUM))
        assertNull(gate.offer(objects, 0, true))
        assertNull(gate.offer(objects, 400, true))
        assertNull(gate.offer(objects, 800, false))
        assertEquals(objects.single().description(), gate.offer(objects, 1_200, true))
        assertNull(gate.offer(objects, 8_000, true))
        assertNotNull(gate.offer(objects, 22_000, true))
        assertNull(gate.offer(emptyList(), 50_000, true))
    }
}
