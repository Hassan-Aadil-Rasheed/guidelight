package com.guidelight.app.indoor

import org.junit.Assert.*
import org.junit.Test

class SavedMapRecognitionTest {
    private fun match(node: Int = 7, x: Float = 1f, inliers: Int = 40, rejected: Int = 0) =
        PlaceMatch(node, inliers, 60, rejected, .6f, x, 0f, 0f)
    @Test fun requiresThreeSeparatedGeometricallyVerifiedMatches() {
        val gate = SavedMapRecognition()
        assertFalse(gate.observe(match(), 0))
        assertFalse(gate.observe(match(), 1000))
        assertTrue(gate.observe(match(), 2000))
    }
    @Test fun repeatedRenderOfOneResultDoesNotCountAsMultipleFrames() {
        val gate = SavedMapRecognition()
        repeat(10) { assertFalse(gate.observe(match(), it * 20L)) }
    }
    @Test fun lowQualityRejectedAndMissingMatchesNeverRecognize() {
        listOf(match(inliers = 10), match(rejected = 1), match(node = 0), match(x = Float.NaN)).forEach { poor ->
            val gate = SavedMapRecognition()
            repeat(5) { assertFalse(gate.observe(poor, it * 1000L)) }
        }
    }
    @Test fun inconsistentPosesDoNotBecomeAPlaceFix() {
        val gate = SavedMapRecognition()
        repeat(5) { assertFalse(gate.observe(match(x = it * 3f), it * 1000L)) }
    }
    @Test fun oldEvidenceExpiresAndPauseResetsIt() {
        val gate = SavedMapRecognition()
        repeat(3) { gate.observe(match(), it * 1000L) }
        assertTrue(gate.recognized(3000))
        assertFalse(gate.recognized(6000))
        gate.reset()
        assertFalse(gate.observe(match(), 7000))
    }
    @Test fun aPositionJumpCannotReuseThePreviousRoomFix() {
        val gate = SavedMapRecognition()
        repeat(3) { gate.observe(match(), it*1000L) }
        assertEquals(7,gate.verifiedNode(2000))
        assertFalse(gate.observe(match(node=9,x=10f),3000))
        assertNull(gate.verifiedNode(3000))
        assertFalse(gate.observe(match(node=9,x=10f),4000))
        assertTrue(gate.observe(match(node=9,x=10f),5000))
        assertEquals(9,gate.verifiedNode(5000))
    }
}
