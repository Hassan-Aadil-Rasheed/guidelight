package com.guidelight.app.remote

import org.junit.Assert.*
import org.junit.Test

class BubbleReadinessGateTest {
    private val bubble = BubbleWindow(1, true, 1000, 700, 1080, 780)

    @Test fun waitsForBothTheFocusedEditorAndASettledBubble() {
        val gate = BubbleReadinessGate()
        assertFalse(gate.ready(bubble, false, 0))
        assertFalse(gate.ready(bubble, true, 1000))
        assertFalse(gate.ready(bubble, true, 1399))
        assertTrue(gate.ready(bubble, true, 1400))
    }
    @Test fun keyboardMovementRestartsTheSettlePeriod() {
        val gate = BubbleReadinessGate()
        gate.ready(bubble, true, 0)
        val moved = bubble.copy(top = 900, bottom = 980)
        assertFalse(gate.ready(moved, true, 350))
        assertFalse(gate.ready(moved, true, 500))
        assertTrue(gate.ready(moved, true, 750))
    }
    @Test fun lostFocusOrHiddenBubbleCannotReuseEarlierReadiness() {
        val gate = BubbleReadinessGate()
        gate.ready(bubble, true, 0)
        assertTrue(gate.ready(bubble, true, 400))
        assertFalse(gate.ready(null, true, 500))
        assertFalse(gate.ready(bubble, true, 1000))
        assertFalse(gate.ready(bubble, false, 1100))
        assertFalse(gate.ready(bubble, true, 2000))
    }
    @Test fun aNewSessionOrWindowMustSettleAgain() {
        val gate = BubbleReadinessGate()
        gate.ready(bubble, true, 0)
        gate.clear()
        assertFalse(gate.ready(bubble, true, 1000))
        assertFalse(gate.ready(bubble.copy(id = 2), true, 1400))
        assertTrue(gate.ready(bubble.copy(id = 2), true, 1800))
    }
}
