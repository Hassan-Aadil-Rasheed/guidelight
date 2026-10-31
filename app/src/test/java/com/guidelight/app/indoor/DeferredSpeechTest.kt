package com.guidelight.app.indoor

import com.guidelight.app.domain.DeferredSpeech
import org.junit.Assert.*
import org.junit.Test

class DeferredSpeechTest {
    @Test fun readyForegroundResponsesPlayImmediately() {
        val gate = DeferredSpeech()
        assertTrue(gate.allow(true, true))
        assertFalse(gate.take(true, true, false))
    }
    @Test fun initializationDelayRemembersThatCurrentResponseNeedsReading() {
        val gate = DeferredSpeech()
        assertFalse(gate.allow(false, true))
        assertFalse(gate.take(false, true, false))
        assertTrue(gate.take(true, true, false))
        assertFalse(gate.take(true, true, false))
    }
    @Test fun temporaryBackgroundResponseWaitsUntilForeground() {
        val gate = DeferredSpeech()
        assertFalse(gate.allow(true, false))
        assertFalse(gate.take(true, false, false))
        assertTrue(gate.take(true, true, false))
    }
    @Test fun neverReplaysWhileWisprIsDictating() {
        val gate = DeferredSpeech()
        gate.defer()
        assertFalse(gate.take(true, true, true))
        assertTrue(gate.take(true, true, false))
    }
    @Test fun armingInputClearsPendingSpeech() {
        val gate = DeferredSpeech()
        gate.defer(); gate.clear()
        assertFalse(gate.take(true, true, false))
    }
    @Test fun aNewForegroundResponseReplacesPendingSpeechWithoutRepeatingIt() {
        val gate = DeferredSpeech()
        assertFalse(gate.allow(false, true))
        assertTrue(gate.allow(true, true))
        assertFalse(gate.take(true, true, false))
    }
}
