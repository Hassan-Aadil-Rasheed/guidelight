package com.guidelight.app.remote

import org.junit.Assert.*
import org.junit.Test

class RemoteHoldGateTest {
    @Test fun renamedButtonAndFactoryHidNameBothWorkWithoutAcceptingUnrelatedKeys() {
        for (name in listOf("GuideLight button", "guidelight button", " GuideLight   Button ", "GuideLight", "Selfie")) {
            val gate = RemoteHoldGate()
            assertEquals(name, RemoteHoldGate.Result.IGNORE, gate.down(7, name, 0, false))
            assertEquals(name, RemoteHoldGate.Result.START, gate.down(7, name, 0, true))
            assertTrue(gate.up(7))
        }
        for (name in listOf(null, "", "gpio-keys", "Phone volume", "Other GuideLight button", "GuideLight headphones")) {
            assertFalse(name, GuideLightButton.matches(name))
            assertEquals(RemoteHoldGate.Result.IGNORE, RemoteHoldGate().down(7, name, 0, true))
        }
    }
    @Test fun onlyEnabledSelfiePressCanStart() {
        val gate = RemoteHoldGate()
        assertEquals(RemoteHoldGate.Result.IGNORE, gate.down(1, "gpio-keys", 0, true))
        assertEquals(RemoteHoldGate.Result.IGNORE, gate.down(2, "Selfie", 0, false))
        assertEquals(RemoteHoldGate.Result.START, gate.down(2, "Selfie", 0, true))
    }
    @Test fun repeatsNeverStartAnotherHold() {
        val gate = RemoteHoldGate()
        assertEquals(RemoteHoldGate.Result.START, gate.down(2, "Selfie", 0, true))
        repeat(20) { assertEquals(RemoteHoldGate.Result.REPEAT, gate.down(2, "Selfie", it + 1, true)) }
    }
    @Test fun anotherDeviceCannotReleaseOrTakeOver() {
        val gate = RemoteHoldGate()
        gate.down(2, "Selfie", 0, true)
        assertFalse(gate.up(1))
        assertEquals(RemoteHoldGate.Result.IGNORE, gate.down(3, "Selfie", 0, true))
        assertTrue(gate.up(2))
        assertFalse(gate.up(2))
    }
    @Test fun cancelledHoldCanBeStartedAgain() {
        val gate = RemoteHoldGate()
        gate.down(2, "Selfie", 0, true)
        assertTrue(gate.cancel())
        assertNull(gate.activeDevice)
        assertEquals(RemoteHoldGate.Result.START, gate.down(2, "Selfie", 0, true))
    }
    @Test fun repeatWithoutInitialDownIsIgnored() {
        assertEquals(RemoteHoldGate.Result.IGNORE, RemoteHoldGate().down(2, "Selfie", 3, true))
    }
}
