package com.guidelight.app.domain

import org.junit.Assert.*
import org.junit.Test

class GuidanceSpeechTest {
    private var dictating = false
    private val events = mutableListOf<String>()
    private val speaker = GuidanceSpeech({ dictating }, { events += "stop" },
        { text, append -> events += "${if (append) "add" else "flush"}:$text" })

    private fun begin() { dictating = true; speaker.silence() }

    @Test fun startingDictationCutsOffTheCurrentUtterance() {
        speaker.say("Caution. Possible obstacle ahead.")
        begin()
        assertEquals(listOf("flush:Caution. Possible obstacle ahead.", "stop"), events)
    }
    @Test fun cameraWarningsAndTurnsCannotSpeakDuringRecording() {
        begin()
        speaker.say("Caution. Possible obstacle ahead.")
        speaker.say("Stop. Possible obstacle on your left.")
        speaker.say("Turn right")
        assertEquals(listOf("stop"), events)
    }
    @Test fun releasingTheButtonDoesNotUnmuteWhileTextIsPending() {
        begin()
        // The input remains armed while Wispr submits and the debounce runs.
        speaker.say("Caution. Possible obstacle ahead.")
        speaker.say("Turn left")
        assertEquals(listOf("stop"), events)
        dictating = false
        speaker.say("End this walk? Say yes.")
        assertEquals("flush:End this walk? Say yes.", events.last())
    }
    @Test fun mutedMessagesAreNotReplayedAfterACommand() {
        begin()
        speaker.say("Old caution")
        speaker.say("Old turn")
        dictating = false
        speaker.say("Walk ended.")
        assertEquals(listOf("stop", "flush:Walk ended."), events)
    }
    @Test fun retryStaysSilentAndCancellationAllowsFreshGuidance() {
        begin()
        speaker.say("Caution")
        begin()
        speaker.say("Turn left")
        dictating = false
        speaker.say("Voice input cancelled.")
        assertEquals(listOf("stop", "stop", "flush:Voice input cancelled."), events)
    }
    @Test fun suggestionQueuesUseTheSameMuteCheck() {
        begin()
        speaker.sequence(listOf("Suggestion 1", "Suggestion 2"))
        assertEquals(listOf("stop"), events)
        dictating = false
        speaker.sequence(listOf("Suggestion 1", "Suggestion 2"))
        assertEquals(listOf("stop", "flush:Suggestion 1", "add:Suggestion 2"), events)
    }
    @Test fun timedOutInputCanSpeakTheRecoveryMessage() {
        begin()
        speaker.say("Caution")
        dictating = false
        speaker.say("Voice input timed out. Try again.")
        assertEquals(listOf("stop", "flush:Voice input timed out. Try again."), events)
    }
}
