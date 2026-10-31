package com.guidelight.app.remote

import org.junit.Assert.*
import org.junit.Test

class BubbleTargetSelectorTest {
    private val bubble = BubbleWindow(1, true, 836, 761, 1061, 986)
    private fun select(vararg windows: BubbleWindow) = BubbleTargetSelector.select(windows.toList(), 1080, 2412, 3f)
    @Test fun actualPhoneBubbleBoundsAreAccepted() { assertEquals(bubble, select(bubble)) }
    @Test fun unrelatedOverlayIsRejected() { assertNull(select(bubble.copy(isWispr = false))) }
    @Test fun fullWidthTransparentWisprWindowIsRejected() { assertNull(select(BubbleWindow(2, true, 0, 2024, 1080, 2412))) }
    @Test fun twoPossibleBubblesAreRejected() { assertNull(select(bubble, bubble.copy(id = 3, top = 1200, bottom = 1425))) }
    @Test fun offscreenOrNonEdgeTargetsAreRejected() {
        assertNull(select(bubble.copy(top = 2500, bottom = 2725)))
        assertNull(select(bubble.copy(left = 430, right = 655)))
    }
}
