package com.guidelight.app.domain

import com.guidelight.app.navigation.GeoPoint
import com.guidelight.app.navigation.NearbyCategory
import com.guidelight.app.navigation.PlaceResult
import com.guidelight.app.indoor.*
import com.guidelight.app.vision.*
import org.junit.Assert.*
import org.junit.Test

class NaturalVoiceCommandsTest {
    private val places = listOf(
        PlaceResult("City Park", "North", GeoPoint(0.0, 0.0), 100),
        PlaceResult("City Park", "South", GeoPoint(0.0, .001), 200),
        PlaceResult("Rose Garden", "Main", GeoPoint(0.0, .002), 300))

    @Test fun courtesyFormattingAndContractionsPreserveCommandMeaning() {
        assertEquals("what is ahead", VoicePhrases.normalize("Could you please tell me what’s ahead? Thanks.").removePrefix("tell me "))
        assertEquals("do not stop navigation", VoicePhrases.normalize("Please don’t stop navigation!"))
        assertEquals("choose suggestion -1", VoicePhrases.normalize("Choose suggestion -1."))
        assertEquals("Coffee For Me", VoicePhrases.politeText("Coffee For Me", allowForMe = false))
    }

    @Test fun naturalWalkingPhrasesCoverExistingTools() {
        mapOf("Could you say that again for me?" to WalkVoiceAction.REPEAT,
            "Please check for obstacles." to WalkVoiceAction.SCAN,
            "What’s ahead of me?" to WalkVoiceAction.SCAN,
            "Read the last warning, please." to WalkVoiceAction.REPEAT_ALERT,
            "Bring up the map." to WalkVoiceAction.SHOW_MAP,
            "Switch to the rear camera." to WalkVoiceAction.SHOW_CAMERA,
            "Show live view." to WalkVoiceAction.SHOW_CAMERA,
            "Could you follow my position?" to WalkVoiceAction.RECENTER,
            "Show the route from start to finish." to WalkVoiceAction.FULL_ROUTE,
            "How much further?" to WalkVoiceAction.LOCATION,
            "Is my camera working?" to WalkVoiceAction.CAMERA_STATUS,
            "Open the options." to WalkVoiceAction.OPEN_CONTROLS,
            "Show the controls, thanks." to WalkVoiceAction.OPEN_CONTROLS,
            "Dismiss the menu." to WalkVoiceAction.CLOSE_CONTROLS,
            "What commands are available?" to WalkVoiceAction.HELP,
            "I’m done walking." to WalkVoiceAction.END).forEach { (text, action) ->
                assertEquals(text, action, WalkVoiceCommands.resolve(text, false))
            }
    }

    @Test fun negationMixedActionsAndBareStopCannotEndAWalk() {
        listOf("don't end my walk", "could you not stop the walk", "stop", "stop the walk and show map",
            "cancel the walk or repeat directions", "no stop navigation", "the sign says stop navigation").forEach {
                assertEquals(it, WalkVoiceAction.UNKNOWN, WalkVoiceCommands.resolve(it, false))
            }
        assertEquals(WalkVoiceAction.KEEP_WALKING, WalkVoiceCommands.resolve("Please don’t stop navigation.", true))
        assertEquals(WalkVoiceAction.END, WalkVoiceCommands.resolve("Yeah, please.", true))
        assertEquals(WalkVoiceAction.UNKNOWN, WalkVoiceCommands.resolve("Yeah, please.", false))
    }

    @Test fun naturalNumberAndOrdinalChoicesResolveOnlyAgainstCurrentResults() {
        listOf("first one", "the first place", "could you use option number one", "I’ll take the first one", "pick suggestion won").forEach {
            assertEquals(it, PlaceChoiceAction.Choose(places[0]), PlaceChoiceVoice.resolve(it, places))
        }
        listOf("go with the second option", "take me to result number two", "I’d like the second one", "option too", "option no. 2").forEach {
            assertEquals(it, PlaceChoiceAction.Choose(places[1]), PlaceChoiceVoice.resolve(it, places))
        }
        assertEquals(PlaceChoiceAction.Choose(places.last()), PlaceChoiceVoice.resolve("take the last one", places))
        assertEquals(PlaceChoiceAction.Choose(places.last()), PlaceChoiceVoice.resolve("third result", places))
    }

    @Test fun uniqueNamesWorkAndDuplicateNamesStillRequireANumber() {
        listOf("Rose Garden", "Take me to Rose Garden.", "could you select Rose Garden please").forEach {
            assertEquals(it, PlaceChoiceAction.Choose(places[2]), PlaceChoiceVoice.resolve(it, places))
        }
        assertTrue(PlaceChoiceVoice.resolve("City Park", places) is PlaceChoiceAction.Invalid)
        assertTrue(PlaceChoiceVoice.resolve("take me there", places) is PlaceChoiceAction.Invalid)
        assertEquals(PlaceChoiceAction.Choose(places[0]), PlaceChoiceVoice.resolve("take me there", places.take(1)))
    }

    @Test fun incorrectAmbiguousAndNegatedSelectionsDoNotChooseAResult() {
        listOf("yes", "first or second", "not the first one", "don't take the second", "option -1", "option zero", "99999999999999999", "for", "too").forEach {
            assertTrue(it, PlaceChoiceVoice.resolve(it, places) is PlaceChoiceAction.Invalid)
        }
        assertEquals(PlaceChoiceAction.Repeat, PlaceChoiceVoice.resolve("What did you find?", places))
        assertEquals(PlaceChoiceAction.ChangeDestination, PlaceChoiceVoice.resolve("Could you find another place?", places))
    }

    @Test fun destinationRequestsKeepNamesAndRemoveOnlyKnownRequestWrappers() {
        listOf("Could you please guide me to India Gate?", "Help me walk to India Gate", "How can I reach India Gate?",
            "I would like to visit India Gate", "Show me walking directions to India Gate", "Get me to India Gate").forEach {
            assertEquals(it, "India Gate", DestinationParser.parse(it))
        }
        listOf("Gateway to India", "Coffee For Me", "Dr Azad Homoeopathic Clinic", "Nehru Place").forEach {
            assertEquals(it, DestinationParser.parse(it))
        }
        assertNull(DestinationParser.parse("Don't take me to India Gate"))
    }

    @Test fun everydayNearbyTermsDoNotReplaceNamedOrCitySpecificSearches() {
        mapOf("Could you find me a chemest close by?" to NearbyCategory.CHEMIST,
            "local medical stores" to NearbyCategory.CHEMIST, "kirana stores around here" to NearbyCategory.GROCERIES,
            "restraunts near here" to NearbyCategory.RESTAURANT, "cash points around me" to NearbyCategory.ATM,
            "homeopathy clinics nearby" to NearbyCategory.CLINIC).forEach { (text, category) ->
            assertEquals(text, category, NearbyCategory.fromQuery(text))
        }
        listOf("Apollo Pharmacy", "medical shop in Mumbai", "Dr Azad Clinic", "don't find a chemist").forEach { assertNull(NearbyCategory.fromQuery(it)) }
    }

    @Test fun homeRoomAndRecordingCommandsAcceptNaturalRequests() {
        val rooms = listOf(HomeRoom("kitchen", 0), HomeRoom("hall", 1))
        assertEquals(HomeCommand.StartRecording, HomeCommands.parse("Could you begin mapping?", rooms))
        assertEquals(HomeCommand.SaveRoom("kitchen"), HomeCommands.parse("Please call this room kitchen.", rooms))
        assertEquals(HomeCommand.GoTo("kitchen"), HomeCommands.parse("Guide me to the kitchen", rooms))
        assertEquals(HomeCommand.Choose(1), HomeCommands.parse("I would like the second room", rooms))
        assertEquals(HomeCommand.ListRooms, HomeCommands.parse("What rooms have I saved?", rooms))
        assertEquals(HomeCommand.Unknown, HomeCommands.parse("Don't end my walk", rooms))
        assertEquals(HomeCommand.Unknown, HomeCommands.parse("name this kitchen and end walk", rooms))
    }

    @Test fun savedHomeControlsAndAiTasksRemainDistinctAndRejectNegations() {
        assertEquals(SavedHomeControl.SAVE, SavedHomeControls.parse("Would you save the home map for me?"))
        assertEquals(SavedHomeControl.LOCATE, SavedHomeControls.parse("Could you tell me where I am?"))
        assertEquals(LocalAiTask.DESCRIBE, LocalAiCommands.parse("Could you describe my surroundings?"))
        assertEquals(LocalAiTask.IDENTIFY_ROOM, LocalAiCommands.parse("Recognise this room, please."))
        assertEquals(LocalAiTask.DETAILED_VIEW, LocalAiCommands.parse("Give me a detailed description."))
        assertTrue(LocalAiCommands.isCancel("Please stop the analysis."))
        assertNull(LocalAiCommands.parse("Don't describe the view"))
        assertNull(SavedHomeControls.parse("Don't save the map"))
    }
}
