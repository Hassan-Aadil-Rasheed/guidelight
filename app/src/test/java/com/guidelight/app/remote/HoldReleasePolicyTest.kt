package com.guidelight.app.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class HoldReleasePolicyTest {
    @Test fun immediateGestureCallbackDoesNotClaimReady() {
        assertEquals(HoldReleasePolicy.Action.WAIT, HoldReleasePolicy.action(5, 305, false, 700))
        assertEquals(HoldReleasePolicy.Action.HELD, HoldReleasePolicy.action(700, 1000, false, 700))
    }
    @Test fun earlyReleaseWaitsForLongPressToAvoidTapRecording() {
        assertEquals(HoldReleasePolicy.Action.WAIT, HoldReleasePolicy.action(250, 550, true, 700))
        assertEquals(HoldReleasePolicy.Action.RELEASE, HoldReleasePolicy.action(700, 1000, true, 700))
    }
    @Test fun physicalReleaseOrDisconnectEndsEstablishedContact() {
        assertEquals(HoldReleasePolicy.Action.RELEASE, HoldReleasePolicy.action(2000, 2300, true, 700))
    }
    @Test fun missingReleaseIsBoundedByThirtySecondLimit() {
        assertEquals(HoldReleasePolicy.Action.HELD, HoldReleasePolicy.action(29_000, 29_300, false, 700))
        assertEquals(HoldReleasePolicy.Action.RELEASE, HoldReleasePolicy.action(29_700, 30_000, false, 700))
    }
}
