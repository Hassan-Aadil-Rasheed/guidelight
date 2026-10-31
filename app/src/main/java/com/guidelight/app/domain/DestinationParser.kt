package com.guidelight.app.domain

object DestinationParser {
    private val request = Regex("^(?:" +
        "(?:i (?:want|need|would like) to )?(?:go|walk|navigate|head|travel)(?: me)?(?: from (?:here|my location))? to|" +
        "(?:take|guide|lead|get|bring|direct) me to|" +
        "help me (?:get|go|walk) to|" +
        "(?:walking directions|directions|a route|the route|the way) to|" +
        "(?:show|give|get|find) (?:me )?(?:walking directions|directions|a route|the route|the way) to|" +
        "how (?:do|can) i (?:get to|go to|walk to|reach)|" +
        "(?:i (?:want|would like|need) to )?(?:visit|reach)|" +
        "(?:i am|i'm) (?:going|walking|heading) to|" +
        "where (?:is|are|can i find)|where's|find me|find|locate|search for|look for|show me" +
        ")\\s+(.+)$", RegexOption.IGNORE_CASE)

    fun parse(transcript: String): String? {
        val normalized = VoicePhrases.politeText(transcript, allowForMe = false)
        if (normalized.isBlank()) return null
        if (Regex("^(?:don't|do not|dont)\\s+(?:go|walk|navigate|take|find|search|show|guide|open|close|enable|disable|clear|change)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(normalized)) return null
        val destination = request.matchEntire(normalized)?.groupValues?.get(1) ?: normalized
        return destination.trim(' ', '.', ',', '?', '!').takeIf { it.length >= 2 }
    }
}

