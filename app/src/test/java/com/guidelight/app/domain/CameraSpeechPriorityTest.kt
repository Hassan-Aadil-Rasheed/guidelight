package com.guidelight.app.domain

import org.junit.Assert.*
import org.junit.Test

class CameraSpeechPriorityTest {
    @Test fun repeatedNavigationUpdatesCannotCutOffACameraUtterance() {
        val priority = CameraSpeechPriority()
        priority.started("camera-warning", 0)
        for (time in listOf(100L, 5_000L, 10_000L, 20_000L)) assertTrue(priority.blocksNavigation(time))
        priority.finished("camera-warning")
        assertFalse(priority.blocksNavigation(20_001))
    }
    @Test fun anOldStoppedUtteranceCannotReleaseANewerWarning() {
        val priority = CameraSpeechPriority()
        priority.started("camera-description", 0)
        priority.started("camera-warning", 100)
        priority.finished("camera-description")
        assertTrue(priority.blocksNavigation(200))
        priority.finished("camera-warning")
        assertFalse(priority.blocksNavigation(201))
    }
    @Test fun engineFailureOrBackgroundResetCannotBlockDirectionsForever() {
        val priority = CameraSpeechPriority()
        priority.started("camera", 0)
        assertFalse(priority.blocksNavigation(30_001))
        priority.started("another", 31_000)
        priority.clear()
        assertFalse(priority.blocksNavigation(31_001))
    }
    @Test fun anAheadObjectIsNotHiddenByABiggerObjectAtTheSide() {
        val observations = ObjectDescriptions.observations(listOf(
            NormalizedBox(0f, .1f, .35f, 1f), NormalizedBox(.45f, .4f, .55f, .8f)))
        assertEquals(ObstaclePosition.CENTER, observations.first().side)
        val gate = ObjectAnnouncementGate()
        assertNull(gate.offer(observations, 0, true))
        assertNull(gate.offer(observations, 400, true))
        assertTrue(gate.offer(observations, 800, true)!!.contains("ahead"))
        gate.reset()
        assertNull(gate.offer(observations, 900, true))
    }
}
