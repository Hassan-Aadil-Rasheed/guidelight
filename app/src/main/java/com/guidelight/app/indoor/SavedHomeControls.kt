package com.guidelight.app.indoor

import com.guidelight.app.domain.VoicePhrases
import com.guidelight.app.domain.WalkVoiceAction
import com.guidelight.app.domain.WalkVoiceCommands

enum class SavedHomeControl { RECORD, SAVE, LOCATE, REPEAT, STOP }

/** Resolve controls before room labels: “save map” must never create a room called map. */
object SavedHomeControls {
    fun parse(text: String): SavedHomeControl? {
        val words = VoicePhrases.normalize(text)
        return when (words) {
            "start mapping", "start recording", "map my home", "create home map", "record my home", "record home", "begin mapping", "begin recording",
            "map my house", "record my house", "start home recording", "start recording my home", "create a home map", "create my home map" -> SavedHomeControl.RECORD
            "save map", "save home", "save home map", "save my home map", "finish mapping", "finish recording", "save the map", "save my map",
            "save the home map", "store the home map", "keep this map", "complete mapping", "complete recording", "done mapping", "done recording" -> SavedHomeControl.SAVE
            "find my position", "find my place", "where am i", "where am i now", "recognize my home",
            "what room am i in", "which room am i in", "tell me my location", "my location", "current location",
            "tell me where i am", "locate me", "find me", "locate my position", "check my position", "check my location", "where are we",
            "which room is this", "what room is this", "identify my location", "recognise my home", "find my location", "show my location" -> SavedHomeControl.LOCATE
            "repeat", "repeat direction", "repeat directions", "repeat guidance" -> SavedHomeControl.REPEAT
            "stop", "end walk", "end the walk", "stop navigation", "finish walk", "stop mapping", "end test", "stop the test", "finish the test",
            "close the test", "cancel mapping", "cancel recording", "cancel the test", "stop the session", "end session" -> SavedHomeControl.STOP
            else -> when (WalkVoiceCommands.resolve(words, false)) {
                WalkVoiceAction.REPEAT -> SavedHomeControl.REPEAT
                WalkVoiceAction.END -> SavedHomeControl.STOP
                else -> null
            }
        }
    }

    fun locationReply(tracked: Boolean, recognized: Boolean, room: String?, cameraStatus: String): String = when {
        !tracked -> "I cannot locate you while camera tracking is unavailable. $cameraStatus"
        !recognized -> "I have not recognized your location yet. Slowly look around at room details you recorded."
        room == null -> "Your home is recognized, but I cannot identify this room yet. Slowly look around."
        else -> "Your location matches the saved $room view."
    }
}
