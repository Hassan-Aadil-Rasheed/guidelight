package com.guidelight.app.domain

import com.guidelight.app.navigation.PlaceResult

sealed interface PlaceChoiceAction {
    data class Choose(val place: PlaceResult) : PlaceChoiceAction
    data object Repeat : PlaceChoiceAction
    data object ChangeDestination : PlaceChoiceAction
    data class Invalid(val message: String) : PlaceChoiceAction
}

/** Interpret a command only against the currently displayed, ordered results. */
object PlaceChoiceVoice {
    private val repeat = VoicePhrases.forms("repeat", "repeat suggestions", "repeat the suggestions", "read suggestions", "read them again",
        "repeat options", "repeat the options", "read the options", "read my options", "repeat the list", "read the list", "list the places",
        "list options", "list the options", "read the results", "repeat results", "what are my choices", "what are my options", "what did you find",
        "read them out", "say that again", "say it again", "tell me again", "again", "read that again", "show the results")
    private val change = VoicePhrases.forms("change destination", "change the destination", "change my destination", "new destination",
        "different destination", "another destination", "change place", "choose another place", "find another place", "search for another place",
        "try another destination", "search again", "start again", "restart search", "cancel search", "cancel the search", "cancel", "go back", "back")

    fun resolve(transcript: String, places: List<PlaceResult>): PlaceChoiceAction {
        val command = VoicePhrases.normalize(transcript)
        if (command in repeat) return PlaceChoiceAction.Repeat
        if (command in change) return PlaceChoiceAction.ChangeDestination
        var place = VoiceSelection.index(command, places.size)?.let(places::getOrNull)
        if (place == null && places.size == 1 && command in VoicePhrases.forms("choose this one", "choose that one", "take me there", "go there", "take that one", "use this one"))
            place = places.single()
        if (place == null) {
            val name = (DestinationParser.parse(transcript) ?: command)
                .let { VoicePhrases.normalize(it) }
                .replace(Regex("^(?:choose|select|pick|use|go with) (?:the )?"), "")
            val matches = places.filter { VoicePhrases.normalize(it.name, courtesy = false) == name }
            if (matches.size > 1) return PlaceChoiceAction.Invalid("More than one suggestion has that name. Say first one, second one, or another suggestion number.")
            place = matches.singleOrNull()
        }
        return if (place != null) PlaceChoiceAction.Choose(place)
        else PlaceChoiceAction.Invalid(if (places.size == 1)
            "Say first one, the place name, repeat suggestions, or change destination."
        else "Say first one, second one, a suggestion number or a unique place name. You can also say repeat suggestions or change destination.")
    }

    fun spokenSuggestions(places: List<PlaceResult>, useRemote: Boolean): List<String> = buildList {
        add("I found ${places.size} ${if (places.size == 1) "suggestion" else "suggestions"}.")
        places.forEachIndexed { index, place -> add("Suggestion ${index + 1}. ${place.spokenLabel()}") }
        add(if (useRemote) "Press your GuideLight button, say first one or a unique place name, then press again to send."
            else "Tap Choose by voice, then use the Wispr Flow bubble to say first one or a unique place name.")
        add("You can also say repeat suggestions or change destination.")
    }
}
