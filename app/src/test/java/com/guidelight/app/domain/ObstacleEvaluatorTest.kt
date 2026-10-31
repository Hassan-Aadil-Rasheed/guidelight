package com.guidelight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ObstacleEvaluatorTest {
    @Test fun largeSideObjectsDoNotWarnEvenWhenTheirEdgesOverlapTheCentre() {
        for (box in listOf(NormalizedBox(.01f, .2f, .6f, .99f), NormalizedBox(.4f, .2f, .99f, .99f))) {
            assertEquals(ObstacleRisk.NONE, ObstacleEvaluator.evaluate(listOf(box)).risk)
        }
    }
    @Test fun ignoresSmallDistantBox() {
        val result = ObstacleEvaluator.evaluate(listOf(NormalizedBox(.4f, .2f, .55f, .5f)))
        assertEquals(ObstacleRisk.NONE, result.risk)
    }

    @Test fun ignoresLargeObjectOutsideWalkingCorridor() {
        val result = ObstacleEvaluator.evaluate(listOf(NormalizedBox(.75f, .3f, 1f, 1f)))
        assertEquals(ObstacleRisk.NONE, result.risk)
    }

    @Test fun returnsCautionForCentralLowerObject() {
        val result = ObstacleEvaluator.evaluate(listOf(NormalizedBox(.35f, .45f, .65f, .78f)))
        assertEquals(ObstacleRisk.CAUTION, result.risk)
        assertEquals(ObstaclePosition.CENTER, result.position)
    }

    @Test fun returnsStopForLargeCentralObject() {
        val result = ObstacleEvaluator.evaluate(listOf(NormalizedBox(.2f, .25f, .8f, .96f)))
        assertEquals(ObstacleRisk.STOP, result.risk)
        assertEquals(ObstaclePosition.CENTER, result.position)
    }

    @Test fun sideObjectTouchingTheOldCorridorDoesNotWarn() {
        val result = ObstacleEvaluator.evaluate(listOf(NormalizedBox(.05f, .4f, .42f, .9f)))
        assertEquals(ObstacleRisk.NONE, result.risk)
    }

    @Test fun upperMiddleAndLowerCentralObjectsCanAllWarn() {
        for ((top, bottom) in listOf(.01f to .3f, .35f to .65f, .7f to .99f)) {
            val result = ObstacleEvaluator.evaluate(listOf(NormalizedBox(.36f, top, .64f, bottom)))
            assertEquals(ObstacleRisk.CAUTION, result.risk)
            assertEquals(ObstaclePosition.CENTER, result.position)
        }
    }
}

