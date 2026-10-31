package com.guidelight.app.domain

import java.util.Locale

/** Whole-phrase matching stays predictable; courtesy words never remove negations. */
object VoicePhrases {
    fun politeText(text: String, allowForMe: Boolean = true): String {
        var value = text.replace('’', '\'').replace('‘', '\'').trim()
            .replace(Regex("\\s+"), " ").trim(' ', '.', ',', '!', '?', ';', ':')
        val prefix = Regex("^(?:please|kindly|can you|could you|would you|will you)\\b[ ,:]*", RegexOption.IGNORE_CASE)
        val suffix = Regex("[ ,:]*\\b(?:please|${if (allowForMe) "for me|" else ""}thank you|thanks)[.!?]*$", RegexOption.IGNORE_CASE)
        while (true) {
            val next = value.replace(prefix, "").replace(suffix, "").trim()
            if (next == value) return value
            value = next
        }
    }

    fun normalize(text: String, courtesy: Boolean = true): String {
        var value = (if (courtesy) politeText(text) else text).lowercase(Locale.ROOT)
            .replace('’', '\'').replace('‘', '\'')
        val contractions = mapOf("what's" to "what is", "whats" to "what is", "where's" to "where is",
            "where am i at" to "where am i", "don't" to "do not", "dont" to "do not",
            "can't" to "cannot", "isn't" to "is not", "i'm" to "i am", "i'd" to "i would", "i'll" to "i will")
        contractions.forEach { (from, to) -> value = value.replace(Regex("\\b${Regex.escape(from)}\\b"), to) }
        return value.replace(Regex("[.,!?;:\"“”]+"), " ").replace(Regex("\\s+"), " ").trim()
    }

    fun forms(vararg phrases: String): Set<String> = phrases.map { normalize(it) }.toSet()
}

/** Numbers and ordinals refer only to the current, bounded suggestion list. */
object VoiceSelection {
    private val cardinals = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty")
    private val ordinals = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth",
        "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth", "seventeenth", "eighteenth", "nineteenth", "twentieth")
    private val tokens = (cardinals + ordinals + listOf("last", "to", "too", "for", "won")).joinToString("|")
    private val selection = Regex("^(?:(?:choose|select|pick|use|take|open|go with|i choose|i pick|i want|i would like|i will take|let's use|let's go with|take me to|navigate to|walk to|start walking to) )?" +
        "(?:the )?(?:(?:suggestion|option|result|place|room|number|no)(?: (?:number|no))? )?" +
        "([0-9]+(?:st|nd|rd|th)?|$tokens)(?: (?:one|option|result|place|room|suggestion))?$")

    fun index(text: String, count: Int): Int? {
        val command = VoicePhrases.normalize(text)
        val token = selection.matchEntire(command)?.groupValues?.get(1) ?: return null
        if (token in setOf("to", "too", "for", "won") && command == token) return null
        val index = when (token) {
            "last" -> count - 1
            "to", "too" -> 1
            "for" -> 3
            "won" -> 0
            else -> token.removeSuffix("st").removeSuffix("nd").removeSuffix("rd").removeSuffix("th").toIntOrNull()?.minus(1)
                ?: cardinals.indexOf(token).takeIf { it >= 0 } ?: ordinals.indexOf(token)
        }
        return index.takeIf { it in 0 until count }
    }
}
