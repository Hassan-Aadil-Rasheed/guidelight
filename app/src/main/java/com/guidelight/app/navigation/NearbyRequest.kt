package com.guidelight.app.navigation

import com.guidelight.app.domain.DestinationParser
import com.guidelight.app.domain.VoicePhrases

data class NearbyRequest(val category: NearbyCategory?, val term: String, val label: String) {
    companion object {
        fun fromQuery(query: String): NearbyRequest? {
            val input = DestinationParser.parse(query) ?: return null
            NearbyCategory.fromQuery(input)?.let { return NearbyRequest(it, it.label, it.plural) }
            val words = VoicePhrases.normalize(input)
            val start = Regex("^(?:the )?(?:nearest|closest|nearby)\\s+")
            val end = Regex("\\s+(?:near me|near my location|near my current location|around me|near here|around here|close to me|close by)$")
            if (!start.containsMatchIn(words) && !end.containsMatchIn(words)) return null
            val term = words.replace(start, "").replace(end, "").removePrefix("a ").removePrefix("an ").trim()
            if (term.length !in 2..60 || term.split(' ').size > 6 ||
                !Regex("[\\p{L}0-9]+(?:[ '\\-][\\p{L}0-9]+)*").matches(term) ||
                Regex("\\b(?:not|don't|do|and|or|then|in|at|to)\\b").containsMatchIn(term)) return null
            return NearbyRequest(null, term, term)
        }
    }
}
