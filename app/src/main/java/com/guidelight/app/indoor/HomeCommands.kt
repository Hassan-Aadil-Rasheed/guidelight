package com.guidelight.app.indoor

import java.util.Locale
import com.guidelight.app.domain.DestinationParser
import com.guidelight.app.domain.VoicePhrases
import com.guidelight.app.domain.VoiceSelection
import com.guidelight.app.domain.WalkVoiceAction
import com.guidelight.app.domain.WalkVoiceCommands

sealed interface HomeCommand {
    data class SaveRoom(val name: String) : HomeCommand
    data class GoTo(val name: String) : HomeCommand
    data class Choose(val index: Int) : HomeCommand
    data object StartRecording : HomeCommand
    data object FinishRecording : HomeCommand
    data object ListRooms : HomeCommand
    data object Repeat : HomeCommand
    data object EndWalk : HomeCommand
    data object Unknown : HomeCommand
}

object HomeCommands {
    private fun clean(text: String) = text.lowercase(Locale.ENGLISH).trim().trimEnd('.', '!', '?')
        .replace('’', '\'').replace(Regex("\\b(it|that)'s\\b"), "$1 is")
        .replace(Regex("\\bits\\b"), "it is")
        .replace(Regex("\\s+"), " ").removePrefix("please ").removeSuffix(" please")

    fun roomName(text: String): String? {
        val name = clean(text).removePrefix("the ").removePrefix("my ").trim()
        return name.takeIf { it.length in 2..40 && it.split(' ').size <= 5 &&
            Regex("[a-z0-9]+(?:[ '-][a-z0-9]+)*").matches(it) &&
            it.split(' ').none { word -> word in setOf("and", "then", "not", "don't") } }
    }

    fun parse(text: String, rooms: List<HomeRoom>): HomeCommand {
        val value = clean(VoicePhrases.normalize(text))
        when (value) {
            "start recording", "record home", "record paths", "start mapping", "begin recording", "begin mapping", "record my home",
            "start home recording", "start recording my home", "map my home", "record my house" -> return HomeCommand.StartRecording
            "finish recording", "stop recording", "finish mapping", "cancel recording", "end recording", "end mapping",
            "complete recording", "complete mapping", "stop mapping", "finish home recording" -> return HomeCommand.FinishRecording
            "hear my rooms", "hear rooms", "read aloud", "list rooms", "list my rooms", "show my rooms", "show rooms", "read rooms", "read my rooms",
            "repeat rooms", "repeat suggestions", "where can i go", "what rooms are saved", "saved rooms", "my rooms", "list the rooms",
            "list saved rooms", "list the saved rooms", "read the room list", "read the rooms", "what rooms have i saved",
            "which rooms can i go to", "show me my rooms", "show me the saved rooms" -> return HomeCommand.ListRooms
            "repeat directions", "repeat direction", "repeat guidance", "repeat" -> return HomeCommand.Repeat
            "end walk", "stop navigation", "finish walk", "end the walk" -> return HomeCommand.EndWalk
        }
        when (WalkVoiceCommands.resolve(value, false)) {
            WalkVoiceAction.REPEAT -> return HomeCommand.Repeat
            WalkVoiceAction.END -> return HomeCommand.EndWalk
            else -> Unit
        }
        // In Home, requests such as “list kitchen” still mean hearing the saved-room list.
        if (value == "list" || value.startsWith("list ")) return HomeCommand.ListRooms
        Regex("(?:(?:this|that|it) is (?:my |the )?|i am in (?:my |the )?|(?:save|remember) (?:this room as |room )?|(?:name|label|call) (?:this room |the room |this |it )?(?:as )?|(?:mark|record) (?:this room as |this as |room ))(.+)").matchEntire(value)?.let {
            return roomName(it.groupValues[1])?.let(HomeCommand::SaveRoom) ?: HomeCommand.Unknown
        }
        VoiceSelection.index(value, rooms.size)?.let { return HomeCommand.Choose(it) }
        DestinationParser.parse(value)?.takeIf { it != value }?.let {
            return roomName(it)?.let(HomeCommand::GoTo) ?: HomeCommand.Unknown
        }
        return rooms.firstOrNull { roomName(value) == it.name }?.let { HomeCommand.GoTo(it.name) } ?: HomeCommand.Unknown
    }
}
