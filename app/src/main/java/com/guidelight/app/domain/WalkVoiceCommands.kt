package com.guidelight.app.domain

enum class WalkVoiceAction { REPEAT, END, KEEP_WALKING, OPEN_CONTROLS, CLOSE_CONTROLS,
    SHOW_MAP, SHOW_CAMERA, RECENTER, FULL_ROUTE, LOCATION, CAMERA_STATUS, SCAN, REPEAT_ALERT, HELP, UNKNOWN }

object WalkVoiceCommands {
    private val aliases = mapOf(
        WalkVoiceAction.REPEAT to VoicePhrases.forms(
            "repeat", "again", "repeat direction", "repeat directions", "repeat the directions", "repeat instructions", "repeat the instructions",
            "repeat guidance", "repeat the guidance", "say that again", "say it again", "tell me again", "tell me the directions again",
            "read directions", "read the directions", "read the instructions", "read that again", "remind me of the directions",
            "next direction", "next turn", "what is the next turn", "what is next", "which way", "which way do i go",
            "where should i go", "what should i do next", "tell me the way", "repeat the turn", "repeat navigation"),
        WalkVoiceAction.SCAN to VoicePhrases.forms(
            "scan", "scan surroundings", "scan my surroundings", "scan the surroundings", "scan around me", "scan around",
            "scan objects", "scan for objects", "look for objects", "describe objects", "describe the objects", "what objects are there",
            "scan the room", "scan room", "scan the view", "scan this view", "scan the area", "scan the scene", "scan ahead",
            "look around", "look ahead", "check ahead", "check my surroundings", "check the surroundings", "check the room",
            "describe my surroundings", "describe the surroundings", "describe the scene", "describe the view", "describe what you see",
            "tell me what you see", "what do you see", "what can you see", "what is around me", "what is in front of me",
            "what is ahead", "what is ahead of me", "what is there", "is there anything ahead", "check for objects",
            "check for obstacles", "are there any obstacles", "are there obstacles", "any obstacles", "obstacle scan", "check my path", "check the path"),
        WalkVoiceAction.REPEAT_ALERT to VoicePhrases.forms(
            "repeat alert", "repeat the alert", "repeat alerts", "last alert", "read the alert", "read the last alert",
            "say the alert again", "what was the alert", "repeat warning", "repeat the warning", "repeat warnings", "last warning",
            "read the warning", "read the last warning", "what was the warning", "tell me the last warning", "repeat caution", "obstacle alert"),
        WalkVoiceAction.SHOW_MAP to VoicePhrases.forms(
            "map", "show map", "show the map", "open map", "open the map", "display map", "display the map", "view map", "view the map",
            "bring up the map", "show me the map", "let me see the map", "navigation map", "show navigation map", "open navigation map",
            "route map", "show route map", "show the route map", "switch to map", "switch to the map", "map view"),
        WalkVoiceAction.SHOW_CAMERA to VoicePhrases.forms(
            "camera", "show camera", "show the camera", "open camera", "open the camera", "camera view", "show camera view",
            "back to camera", "back to the camera", "return to camera", "return to the camera", "show camera again", "switch to camera",
            "switch to the camera", "switch to rear camera", "switch to the rear camera", "rear camera", "show rear camera", "live camera", "show live camera", "live view", "show live view",
            "show the live view", "camera preview", "show camera preview", "back to live view"),
        WalkVoiceAction.RECENTER to VoicePhrases.forms(
            "recenter", "recentre", "re-center", "re-centre", "recenter map", "recentre map", "recenter the map", "recentre the map",
            "re-center map", "re-centre map", "re center map", "re centre map", "center map", "centre map", "center the map", "centre the map",
            "follow me", "follow my location", "follow my position", "keep the map on me", "bring the map back to me",
            "put me in the center", "put me in the centre", "find me on the map", "show my position on the map"),
        WalkVoiceAction.FULL_ROUTE to VoicePhrases.forms(
            "full route", "show full route", "show the full route", "show my full route", "entire route", "show entire route", "show the entire route",
            "whole route", "show the whole route", "route overview", "show route overview", "overview of the route", "overview of my walk",
            "show my whole walk", "show the entire walk", "show the whole journey", "fit route", "fit the route", "zoom out to the route",
            "show the route from start to finish"),
        WalkVoiceAction.LOCATION to VoicePhrases.forms(
            "location", "location status", "my location", "current location", "my current location", "where am i", "where are we",
            "where am i now", "where are we now", "tell me my location", "tell me where i am", "check my location", "check location", "position status",
            "gps", "gps status", "gps signal", "check gps", "check the gps", "location accuracy", "gps accuracy", "how good is my location",
            "how far is left", "how far left", "how much further", "how much farther", "how far to go", "distance remaining", "remaining distance",
            "how long is left", "how much time is left", "time remaining", "remaining time", "are we there yet", "route progress", "walking progress"),
        WalkVoiceAction.CAMERA_STATUS to VoicePhrases.forms(
            "camera status", "scanning status", "scanner status", "check camera", "check the camera", "check camera status",
            "is the camera working", "is my camera working", "is the camera ready", "is scanning working", "is scanning active",
            "are you scanning", "are you still scanning", "is the scanner working", "check scanning", "check the scanner"),
        WalkVoiceAction.OPEN_CONTROLS to VoicePhrases.forms(
            "menu", "open menu", "open the menu", "show menu", "show the menu", "show me the menu", "bring up the menu",
            "controls", "open controls", "open the controls", "show controls", "show the controls", "show me the controls", "walking controls", "open walking controls",
            "options", "show options", "show the options", "open options", "open the options", "show me options", "show me controls", "tools", "show tools", "open tools", "walking options", "settings", "walking settings"),
        WalkVoiceAction.CLOSE_CONTROLS to VoicePhrases.forms(
            "close menu", "close the menu", "close controls", "close the controls", "hide menu", "hide the menu", "hide controls", "hide the controls",
            "dismiss menu", "dismiss the menu", "close panel", "close the panel", "hide panel", "close map", "close the map", "hide map", "hide the map",
            "close help", "hide help", "go back", "back", "back to the walk", "back to walking", "return to the walk", "cancel"),
        WalkVoiceAction.HELP to VoicePhrases.forms(
            "help", "help me", "show help", "open help", "voice help", "command help", "help with commands", "what can i say",
            "what commands can i say", "what are the commands", "what commands are available", "what can you do", "list commands",
            "list the commands", "show commands", "show the commands", "read commands", "read the commands", "available commands",
            "commands", "voice commands", "how do i use this", "how do i use you", "how does the button work", "how do i use the selfie button",
            "how do i use the guidelight button", "how do i use the guide light button",
            "how do i use guidelight button", "how do i use guide light button"),
        WalkVoiceAction.END to VoicePhrases.forms(
            "end walk", "end the walk", "end my walk", "end this walk", "stop walk", "stop the walk", "stop my walk", "stop walking",
            "finish walk", "finish the walk", "finish my walk", "finish this walk", "stop navigation", "stop the navigation", "end navigation",
            "end the navigation", "finish navigation", "cancel navigation", "cancel the navigation", "exit navigation", "quit navigation",
            "stop guidance", "stop the guidance", "end guidance", "stop directions", "stop the directions", "cancel the walk", "cancel my walk",
            "cancel this walk", "end route", "end the route", "cancel the route", "finish the route", "end trip", "end the trip", "finish the trip",
            "leave walking mode", "turn off navigation", "i am done walking", "i am done with this walk", "i have finished walking",
            "i want to end the walk", "i want to stop navigation")
    )
    private val confirm = VoicePhrases.forms("yes", "yeah", "yep", "yup", "sure", "okay", "ok", "confirm", "go ahead",
        "yes end walk", "yes end the walk", "yes stop navigation", "confirm end walk", "confirm end the walk", "end it", "finish it")
    private val keepWalking = VoicePhrases.forms("no", "nope", "nah", "not now", "cancel", "cancel that", "never mind", "nevermind",
        "keep walking", "continue walking", "continue", "keep going", "no keep walking", "no keep going", "do not end the walk",
        "do not stop", "do not stop navigation", "do not end my walk", "keep guiding me", "i want to keep walking", "cancel end walk")

    fun resolve(transcript: String, confirmingEnd: Boolean): WalkVoiceAction {
        val command = VoicePhrases.normalize(transcript)
        if (confirmingEnd && command in keepWalking) return WalkVoiceAction.KEEP_WALKING
        if (confirmingEnd && command in confirm) return WalkVoiceAction.END
        return aliases.entries.firstOrNull { command in it.value }?.key ?: WalkVoiceAction.UNKNOWN
    }

    fun help(confirmingEnd: Boolean) = if (confirmingEnd)
        "Say yes to end the walk, or keep walking to continue."
    else "Say repeat directions, scan surroundings, repeat alert, show map, recenter, full route, camera view, location status, camera status, open controls, close controls, help, or end walk. You can also say look around, which way, or how much further."
}
