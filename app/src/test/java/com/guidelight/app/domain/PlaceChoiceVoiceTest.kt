package com.guidelight.app.domain

import com.guidelight.app.navigation.GeoPoint
import com.guidelight.app.navigation.PlaceResult
import org.junit.Assert.*
import org.junit.Test

class PlaceChoiceVoiceTest {
    private val places = listOf(
        PlaceResult("City Park", "North entrance", GeoPoint(28.6, 77.2), 500),
        PlaceResult("City Park", "South entrance", GeoPoint(28.7, 77.3), 1200),
        PlaceResult("Rose Garden", "Garden Road", GeoPoint(28.8, 77.4), 2300)
    )

    @Test fun spokenNumbersChooseTheExactResultEvenWithDuplicateNames() {
        assertEquals(PlaceChoiceAction.Choose(places[1]), PlaceChoiceVoice.resolve("Choose suggestion two.", places))
        assertEquals(PlaceChoiceAction.Choose(places[0]), PlaceChoiceVoice.resolve("choose suggestion 1", places))
        assertEquals(PlaceChoiceAction.Choose(places[2]), PlaceChoiceVoice.resolve("CHOOSE SUGGESTION THREE!", places))
    }
    @Test fun acceptsCommonWisprFormattingAndOrdinalCommands() {
        listOf("Please select option number 2.", "pick the second one", "  suggestion   two  ", "2", "choose 2nd please").forEach {
            assertEquals(it, PlaceChoiceAction.Choose(places[1]), PlaceChoiceVoice.resolve(it, places))
        }
    }
    @Test fun rejectsMissingOutOfRangeNegativeAndOverflowNumbers() {
        listOf("", "choose suggestion 0", "choose suggestion 4", "choose suggestion -1", "9999999999999999").forEach {
            assertTrue(it, PlaceChoiceVoice.resolve(it, places) is PlaceChoiceAction.Invalid)
        }
    }
    @Test fun refusesAmbiguousCommandsAndUnrelatedDestinations() {
        listOf("choose suggestion 1 or 2", "do not choose suggestion 1", "I want to go to sector 2", "one two three").forEach {
            assertTrue(it, PlaceChoiceVoice.resolve(it, places) is PlaceChoiceAction.Invalid)
        }
    }
    @Test fun repeatAndChangeDestinationAreSeparateFromSelection() {
        assertEquals(PlaceChoiceAction.Repeat, PlaceChoiceVoice.resolve("Repeat suggestions.", places))
        assertEquals(PlaceChoiceAction.ChangeDestination, PlaceChoiceVoice.resolve("Change destination!", places))
    }
    @Test fun resolvesOnlyAgainstTheCurrentList() {
        val updated = places.reversed()
        assertEquals(PlaceChoiceAction.Choose(updated[0]), PlaceChoiceVoice.resolve("choose suggestion one", updated))
        assertTrue(PlaceChoiceVoice.resolve("choose suggestion one", emptyList()) is PlaceChoiceAction.Invalid)
    }
    @Test fun readsEverySuggestionInScreenOrderWithAddressAndSpokenDistance() {
        val speech = PlaceChoiceVoice.spokenSuggestions(places, true)
        assertEquals("I found 3 suggestions.", speech[0])
        assertEquals("Suggestion 1. City Park, 500 metres away. North entrance", speech[1])
        assertEquals("Suggestion 2. City Park, 1.2 kilometres away. South entrance", speech[2])
        assertTrue(speech[3].startsWith("Suggestion 3. Rose Garden"))
        assertTrue(speech[4].contains("GuideLight button"))
    }
    @Test fun aSingleResultStillRequiresAnExplicitChoice() {
        assertTrue(PlaceChoiceVoice.resolve("yes", places.take(1)) is PlaceChoiceAction.Invalid)
        assertEquals(PlaceChoiceAction.Choose(places[0]), PlaceChoiceVoice.resolve("choose suggestion 1", places.take(1)))
        assertTrue(PlaceChoiceVoice.spokenSuggestions(places.take(1), false).last { it.contains("Wispr") }.contains("Choose by voice"))
    }
}
