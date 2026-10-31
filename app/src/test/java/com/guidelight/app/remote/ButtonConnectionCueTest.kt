package com.guidelight.app.remote

import org.junit.Assert.*
import org.junit.Test

class ButtonConnectionCueTest {
    @Test fun connectionWaitsForDictationToFinishAndIsReadOnlyOnce() {
        val cue = ButtonConnectionCue()
        cue.changed(true)
        repeat(5) { assertNull(cue.take(enabled = true, canSpeak = false)) }
        assertEquals("GuideLight button connected.", cue.take(enabled = true, canSpeak = true))
        assertNull(cue.take(enabled = true, canSpeak = true))
    }
    @Test fun aNewerDisconnectReplacesAConnectionThatWasNeverSpoken() {
        val cue = ButtonConnectionCue()
        cue.changed(true); cue.changed(false)
        assertTrue(cue.take(enabled = true, canSpeak = true)!!.startsWith("GuideLight button disconnected."))
        assertNull(cue.take(enabled = true, canSpeak = true))
    }
    @Test fun disabledOrBackgroundedControlsDoNotReplayStaleEvents() {
        val cue = ButtonConnectionCue()
        cue.changed(true)
        assertNull(cue.take(enabled = false, canSpeak = true))
        assertNull(cue.take(enabled = true, canSpeak = true))
        cue.changed(true); cue.clear()
        assertNull(cue.take(enabled = true, canSpeak = true))
    }
}
