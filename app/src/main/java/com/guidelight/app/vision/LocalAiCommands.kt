package com.guidelight.app.vision

import com.guidelight.app.domain.VoicePhrases

object LocalAiCommands {
    fun isCloudSetup(text: String)=VoicePhrases.normalize(text) in
        setOf("cloudflare setup","set up cloudflare","connect cloudflare","cloudflare settings", "open cloudflare setup", "configure cloudflare", "open cloudflare settings")
    fun parse(text: String): LocalAiTask? = when(VoicePhrases.normalize(text)) {
        "describe view","describe the view","describe this view","what do you see","what can you see",
        "what is in front of me","describe the room","look around", "scan the room", "scan surroundings", "describe my surroundings", "describe the surroundings",
        "describe the scene", "describe this scene", "tell me what you see", "what is around me", "look ahead", "describe what is ahead", "scan the view" -> LocalAiTask.DESCRIBE
        "identify room","identify this room","identify the room","recognize this room","compare this room", "recognise this room", "recognise the room",
        "recognize the room", "what saved room is this", "which saved room is this", "compare the room", "match this room", "check this room" -> LocalAiTask.IDENTIFY_ROOM
        "explain my status","explain the status","why can t you locate me", "why cannot you locate me", "explain my location", "why is my location uncertain",
        "why are you unsure", "why is tracking unavailable", "explain tracking", "explain my tracking status", "what is my tracking status" -> LocalAiTask.EXPLAIN_STATUS
        "detailed analysis","describe in detail","describe the view in detail","use detailed reasoning", "describe this in detail", "tell me more about the view",
        "give me a detailed description", "detailed description", "analyse the view", "analyze the view" -> LocalAiTask.DETAILED_VIEW
        else -> null
    }
    fun isCancel(text: String) = VoicePhrases.normalize(text) in
        setOf("cancel analysis","stop thinking","cancel thinking","stop ai", "cancel the analysis", "stop the analysis", "stop analyzing", "stop analysing",
            "cancel the description", "stop the description", "cancel ai", "cancel this analysis", "stop describing")
}
