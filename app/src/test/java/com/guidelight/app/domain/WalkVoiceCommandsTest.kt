package com.guidelight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WalkVoiceCommandsTest {
    @Test fun allWalkingToolsHaveDirectWisprCommands() {
        mapOf("Open controls." to WalkVoiceAction.OPEN_CONTROLS, "close menu" to WalkVoiceAction.CLOSE_CONTROLS,
            "show map" to WalkVoiceAction.SHOW_MAP, "camera view" to WalkVoiceAction.SHOW_CAMERA,
            "recenter" to WalkVoiceAction.RECENTER, "full route" to WalkVoiceAction.FULL_ROUTE,
            "where am I?" to WalkVoiceAction.LOCATION, "camera status" to WalkVoiceAction.CAMERA_STATUS,
            "scan surroundings" to WalkVoiceAction.SCAN, "repeat alert" to WalkVoiceAction.REPEAT_ALERT,
            "what can I say?" to WalkVoiceAction.HELP).forEach { (text, action) ->
                assertEquals(text, action, WalkVoiceCommands.resolve(text, false))
            }
    }
    @Test fun mixedOrNegatedToolCommandsDoNotTriggerActions() {
        listOf("don't show map", "open controls and end walk", "object on your right", "stop scanning objects").forEach {
            assertEquals(WalkVoiceAction.UNKNOWN, WalkVoiceCommands.resolve(it, false))
        }
    }
    @Test fun acceptsNaturalScanPhrasesFromWisprWithoutMatchingNegations() {
        listOf("Scan the room.", "please look around", "what do you see?", "scan the area", "what is in front of me").forEach {
            assertEquals(it, WalkVoiceAction.SCAN, WalkVoiceCommands.resolve(it, false))
        }
        assertEquals(WalkVoiceAction.UNKNOWN, WalkVoiceCommands.resolve("don't scan the room", false))
    }
    @Test fun acceptsWisprPunctuationAndRepeatVariations() {
        listOf("Repeat directions.", "repeat direction", "PLEASE repeat the instructions!", "repeat directions please").forEach {
            assertEquals(it, WalkVoiceAction.REPEAT, WalkVoiceCommands.resolve(it, false))
        }
    }
    @Test fun explicitEndCommandsStopImmediatelyWithoutConfirmation() {
        listOf("End walk.", "end the walk", "stop navigation", "finish the walk").forEach {
            assertEquals(it, WalkVoiceAction.END, WalkVoiceCommands.resolve(it, false))
            assertEquals(it, WalkVoiceAction.END, WalkVoiceCommands.resolve(it, true))
        }
    }
    @Test fun confirmationOnlyWorksWhenTheCurrentWalkIsAwaitingIt() {
        listOf("confirm end walk", "Yes,", "yes end the walk", "Yes, end walk.").forEach {
            assertEquals(WalkVoiceAction.UNKNOWN, WalkVoiceCommands.resolve(it, false))
            assertEquals(WalkVoiceAction.END, WalkVoiceCommands.resolve(it, true))
        }
    }
    @Test fun keepWalkingCancelsOnlyTheConfirmation() {
        listOf("keep walking", "no", "do not end the walk", "cancel").forEach {
            assertEquals(WalkVoiceAction.KEEP_WALKING, WalkVoiceCommands.resolve(it, true))
        }
    }
    @Test fun repeatRemainsAvailableWhileConfirming() {
        assertEquals(WalkVoiceAction.REPEAT, WalkVoiceCommands.resolve("Repeat directions.", true))
    }
    @Test fun warningsNegationsDestinationsAndMixedCommandsCannotEndNavigation() {
        listOf("stop", "Stop. Possible obstacle ahead.", "don't stop navigation", "end walk or repeat directions", "Nehru Place", "confirm end walk and go home").forEach {
            assertEquals(it, WalkVoiceAction.UNKNOWN, WalkVoiceCommands.resolve(it, false))
            assertNotEquals(it, WalkVoiceAction.END, WalkVoiceCommands.resolve(it, true))
        }
    }
}
