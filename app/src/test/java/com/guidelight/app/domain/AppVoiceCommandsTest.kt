package com.guidelight.app.domain

import org.junit.Assert.*
import org.junit.Test

class AppVoiceCommandsTest {
    @Test fun renamedButtonSetupAndControlsAcceptNaturalCommands() {
        mapOf("GuideLight button setup" to AppVoiceAction.SELFIE_SETUP,
            "Please open guide light button settings" to AppVoiceAction.SELFIE_SETUP,
            "Could you enable the GuideLight button?" to AppVoiceAction.ENABLE_SELFIE,
            "Turn on guide light button" to AppVoiceAction.ENABLE_SELFIE,
            "Disable GuideLight button" to AppVoiceAction.DISABLE_SELFIE,
            "Stop using guide light button" to AppVoiceAction.DISABLE_SELFIE).forEach { (phrase, action) ->
            assertEquals(phrase, action, AppVoiceCommands.resolve(phrase))
        }
        assertNull(AppVoiceCommands.resolve("Don't enable GuideLight button"))
        assertNull(AppVoiceCommands.resolve("GuideLight button shop"))
    }
    @Test fun homeNavigationIsAnAppActionWithNaturalPolitePhrases() {
        listOf("Home navigation", "Please open home navigation.", "Could you take me to home navigation?", "Open home", "navigate indoors")
            .forEach { assertEquals(it, AppVoiceAction.HOME, AppVoiceCommands.resolve(it)) }
    }

    @Test fun navigationDoesNotStealAddressesOrNegatedRequests() {
        listOf("take me home", "Home Depot", "Home Navigation Road", "do not open home", "don't open home navigation", "nearest temple", "go to camera market")
            .forEach { assertNull(it, AppVoiceCommands.resolve(it)) }
        assertNull(DestinationParser.parse("Do not open Home navigation"))
        assertNull(DestinationParser.parse("Don't enable selfie button"))
    }

    @Test fun everySharedAppControlHasAnExactVoiceRoute() {
        val controls = mapOf(
            "open navigation" to AppVoiceAction.NAVIGATE, "open camera" to AppVoiceAction.CAMERA,
            "what can I say" to AppVoiceAction.HELP, "go back" to AppVoiceAction.BACK, "close settings" to AppVoiceAction.CLOSE,
            "selfie button setup" to AppVoiceAction.SELFIE_SETUP, "enable selfie button" to AppVoiceAction.ENABLE_SELFIE,
            "disable selfie button" to AppVoiceAction.DISABLE_SELFIE, "accessibility settings" to AppVoiceAction.ACCESSIBILITY_SETTINGS,
            "camera settings" to AppVoiceAction.APP_SETTINGS, "location settings" to AppVoiceAction.LOCATION_SETTINGS,
            "Cloudflare settings" to AppVoiceAction.CLOUD_SETTINGS, "Google Privacy Policy" to AppVoiceAction.PRIVACY,
            "clear destination" to AppVoiceAction.CLEAR_DESTINATION, "change destination" to AppVoiceAction.CHANGE_DESTINATION,
            "find place" to AppVoiceAction.SEARCH, "read status" to AppVoiceAction.STATUS,
            "cancel command" to AppVoiceAction.CANCEL_INPUT, "stop searching" to AppVoiceAction.CANCEL_SEARCH
        )
        controls.forEach { (phrase, action) -> assertEquals(phrase, action, AppVoiceCommands.resolve(phrase)) }
    }

    @Test fun homeAssistantAndPreviousRecordingsControlsAreRecognized() {
        val controls = mapOf("set up local AI" to HomeToolAction.LOCAL_AI_SETUP,
            "previous room recordings" to HomeToolAction.LEGACY_RECORDINGS, "more options" to HomeToolAction.MORE_OPTIONS,
            "fewer options" to HomeToolAction.FEWER_OPTIONS, "scan home marker" to HomeToolAction.START_CAMERA,
            "stop camera" to HomeToolAction.STOP_CAMERA)
        controls.forEach { (phrase, action) -> assertEquals(phrase, action, HomeToolCommands.resolve(phrase)) }
    }

    @Test fun cloudControlsKeepConsentExplicitAndNeverTreatCredentialsAsActions() {
        assertEquals(CloudSetupAction.CONNECT, CloudSetupCommands.resolve("connect and test"))
        assertEquals(CloudSetupAction.DISCONNECT, CloudSetupCommands.resolve("disconnect Cloudflare"))
        assertEquals(CloudSetupAction.ACCEPT_TERMS, CloudSetupCommands.resolve("I accept the license and use policy"))
        assertEquals(CloudSetupAction.DECLINE_TERMS, CloudSetupCommands.resolve("I do not accept the license"))
        assertNull(CloudSetupCommands.resolve("I do not accept the license and use policy"))
        assertNull(CloudSetupCommands.resolve("my token is abc123"))
        assertNull(CloudSetupCommands.resolve("do not disconnect Cloudflare"))
    }
}
