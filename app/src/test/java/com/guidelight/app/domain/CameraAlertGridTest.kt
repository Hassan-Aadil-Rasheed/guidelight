package com.guidelight.app.domain

import org.junit.Assert.*
import org.junit.Test

class CameraAlertGridTest {
    @Test fun onlyTheThreeMiddleColumnCellsAreFocused() {
        val centres = listOf(.16f, .5f, .84f)
        for ((col, x) in centres.withIndex()) for ((row, y) in centres.withIndex()) {
            val box = NormalizedBox(x - .06f, y - .06f, x + .06f, y + .06f)
            assertEquals(col == 1, CameraAlertGrid.focused(listOf(box)).isNotEmpty())
            assertEquals(ObjectHeight.entries[row], CameraAlertGrid.row(box))
        }
    }

    @Test fun invalidAndTinyDetectionsNeverEnterTheFocusGrid() {
        val boxes = listOf(NormalizedBox(Float.NaN, .1f, .7f, .4f),
            NormalizedBox(.4f, -.1f, .6f, .3f), NormalizedBox(.4f, .4f, .39f, .6f),
            NormalizedBox(.49f, .49f, .51f, .51f))
        assertTrue(CameraAlertGrid.focused(boxes).isEmpty())
    }

    @Test fun onDemandScanRetainsSideObjectsWhileAutomaticFocusDoesNot() {
        val boxes = listOf(NormalizedBox(.02f, .2f, .3f, .9f), NormalizedBox(.43f, .2f, .57f, .45f))
        assertEquals(2, ObjectDescriptions.observations(boxes).size)
        val focused = ObjectDescriptions.observations(CameraAlertGrid.focused(boxes))
        assertEquals(1, focused.size)
        assertEquals(ObstaclePosition.CENTER, focused.single().side)
        assertEquals(ObstacleRisk.NONE, ObstacleEvaluator.evaluate(boxes).risk)
    }

    private fun caution(height: ObjectHeight = ObjectHeight.MIDDLE, size: ApparentSize = ApparentSize.MEDIUM) =
        ObstacleAlert(ObstacleRisk.CAUTION, ObstaclePosition.CENTER,
            ObjectAppearance(ObstaclePosition.CENTER, height, size))

    private fun stable(gate: ObstacleAlertGate, alert: ObstacleAlert, at: Long): ObstacleAlert? {
        val first = gate.offer(alert, at)
        val second = gate.offer(alert, at + 250)
        val third = gate.offer(alert, at + 500)
        return third ?: second ?: first
    }

    @Test fun briefDetectionAndSizeJitterDoNotCauseRepeatedCautions() {
        val gate = ObstacleAlertGate()
        assertEquals(caution(), stable(gate, caution(), 0))
        for (time in 1_000L..29_000L step 1_000) {
            assertNull(gate.offer(caution(size = if (time % 2_000 == 0L) ApparentSize.LARGE else ApparentSize.MEDIUM), time))
        }
        assertNotNull(gate.offer(caution(), 30_500))
    }

    @Test fun oneDroppedFrameDoesNotRearmTheSameWarning() {
        val gate = ObstacleAlertGate()
        stable(gate, caution(), 0)
        assertNull(gate.offer(ObstacleAlert(ObstacleRisk.NONE), 1_000))
        assertNull(stable(gate, caution(), 1_300))
        assertNull(gate.offer(ObstacleAlert(ObstacleRisk.NONE), 2_000))
        assertEquals(ObstacleRisk.NONE, gate.offer(ObstacleAlert(ObstacleRisk.NONE), 2_800)?.risk)
        assertNull(stable(gate, caution(), 3_000))
        assertNotNull(stable(gate, caution(), 9_000))
    }

    @Test fun changedZoneIsPacedButStableEscalationCanInterrupt() {
        val gate = ObstacleAlertGate()
        stable(gate, caution(), 0)
        assertNull(stable(gate, caution(ObjectHeight.HIGH), 1_000))
        val stop = caution(ObjectHeight.LOW).copy(risk = ObstacleRisk.STOP)
        assertEquals(stop, stable(gate, stop, 2_000))
        gate.reset()
        assertEquals(caution(), stable(gate, caution(), 3_000))
    }

    @Test fun unstableObjectsDoNotWarnJustBecauseThreeDifferentBoxesWereSeen() {
        val gate = ObstacleAlertGate()
        assertNull(gate.offer(caution(ObjectHeight.HIGH), 0))
        assertNull(gate.offer(caution(ObjectHeight.MIDDLE), 300))
        assertNull(gate.offer(caution(ObjectHeight.LOW), 600))
        assertNull(gate.offer(ObstacleAlert(ObstacleRisk.NONE), 900))
    }
}
