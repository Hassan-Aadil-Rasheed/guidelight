package com.guidelight.app.domain

enum class AppVoiceAction {
    HOME, NAVIGATE, CAMERA, HELP, BACK, CLOSE, SELFIE_SETUP, ENABLE_SELFIE, DISABLE_SELFIE,
    ACCESSIBILITY_SETTINGS, APP_SETTINGS, LOCATION_SETTINGS, CLOUD_SETTINGS, PRIVACY,
    CLEAR_DESTINATION, CHANGE_DESTINATION, SEARCH, STATUS, CANCEL_INPUT, CANCEL_SEARCH
}

/** App controls are whole phrases, resolved before place names or room labels. */
object AppVoiceCommands {
    private val phrases = mapOf(
        AppVoiceAction.HOME to VoicePhrases.forms("home navigation", "open home navigation", "go to home navigation",
            "take me to home navigation", "open home", "open the home screen", "home screen", "indoor navigation", "open indoor navigation", "navigate indoors"),
        AppVoiceAction.NAVIGATE to VoicePhrases.forms("navigate tab", "navigation", "open navigation", "back to navigation",
            "return to navigation", "go to navigation", "outdoor navigation", "open outdoor navigation", "back to navigate", "open navigate"),
        AppVoiceAction.CAMERA to VoicePhrases.forms("camera", "open camera", "open the camera", "camera tab", "show camera",
            "camera view", "switch to camera", "back to camera", "back to the camera", "show camera view"),
        AppVoiceAction.BACK to VoicePhrases.forms("back", "go back", "go back one screen", "previous screen"),
        AppVoiceAction.CLOSE to VoicePhrases.forms("close help", "close settings", "close setup", "close the help", "close the settings",
            "dismiss help", "dismiss settings", "done", "got it"),
        AppVoiceAction.SELFIE_SETUP to VoicePhrases.forms("selfie button setup", "open selfie button setup", "button setup", "selfie settings",
            "button settings", "open button settings", "open selfie settings", "remote settings",
            "guidelight button setup", "open guidelight button setup", "guidelight button settings", "open guidelight button settings"),
        AppVoiceAction.ENABLE_SELFIE to VoicePhrases.forms("enable selfie button", "turn on selfie button", "use selfie button", "enable the selfie button",
            "enable guidelight button", "turn on guidelight button", "use guidelight button", "enable the guidelight button", "turn on the guidelight button"),
        AppVoiceAction.DISABLE_SELFIE to VoicePhrases.forms("disable selfie button", "turn off selfie button", "stop using selfie button", "disable the selfie button",
            "disable guidelight button", "turn off guidelight button", "stop using guidelight button", "disable the guidelight button", "turn off the guidelight button"),
        AppVoiceAction.ACCESSIBILITY_SETTINGS to VoicePhrases.forms("accessibility settings", "open accessibility settings", "button permission settings"),
        AppVoiceAction.APP_SETTINGS to VoicePhrases.forms("app settings", "open app settings", "camera settings", "camera permissions", "open camera permissions"),
        AppVoiceAction.LOCATION_SETTINGS to VoicePhrases.forms("location settings", "open location settings", "gps settings", "open gps settings"),
        AppVoiceAction.CLOUD_SETTINGS to VoicePhrases.forms("cloudflare setup", "set up cloudflare", "connect cloudflare", "cloudflare settings",
            "open cloudflare setup", "configure cloudflare", "open cloudflare settings"),
        AppVoiceAction.PRIVACY to VoicePhrases.forms("google privacy policy", "open google privacy policy", "privacy policy", "open privacy policy"),
        AppVoiceAction.CLEAR_DESTINATION to VoicePhrases.forms("clear destination", "clear the destination", "erase destination", "clear address", "clear the address"),
        AppVoiceAction.CHANGE_DESTINATION to VoicePhrases.forms("change destination", "change the destination", "change my destination", "new destination", "choose destination"),
        AppVoiceAction.SEARCH to VoicePhrases.forms("find place", "find a place", "search destination", "search this destination", "find this destination", "retry search"),
        AppVoiceAction.STATUS to VoicePhrases.forms("status", "read status", "read my status", "read the status", "tell me the status", "read answer", "repeat answer", "read description", "repeat description"),
        AppVoiceAction.CANCEL_INPUT to VoicePhrases.forms("cancel voice input", "cancel command", "cancel this command", "stop listening"),
        AppVoiceAction.CANCEL_SEARCH to VoicePhrases.forms("cancel search", "cancel the search", "stop searching", "stop the search")
    )

    fun resolve(text: String): AppVoiceAction? {
        if (WalkVoiceCommands.resolve(text, false) == WalkVoiceAction.HELP) return AppVoiceAction.HELP
        val command = VoicePhrases.normalize(text).replace("guide light button", "guidelight button")
        return phrases.entries.firstOrNull { command in it.value }?.key
    }

    val navigationHelp = "Say Home navigation to open your rooms, open camera, back to navigation, clear destination, change destination, " +
        "find place, cancel search, location status, camera status, GuideLight button setup, enable or disable GuideLight button, Accessibility settings, app settings, location settings, Cloudflare settings, privacy policy, go back, close settings, " +
        "or what can I say. To travel, say a place or nearest followed by a place type. For suggestions, say first one, repeat suggestions, or change destination."
    val homeHelp = "Say create home map, save map, find my position, list my rooms, describe view, identify room, explain my status, " +
        "detailed analysis, cancel analysis, set up local AI, Cloudflare settings, more options, fewer options, previous room recordings, " +
        "read status, end test, or back to navigation. Name a room by saying this is my kitchen, or another room name. You can also say open camera, GuideLight button setup, app settings, or privacy policy."
    val legacyHelp = "Say scan home marker, start recording, finish recording, this is my kitchen or another room name, list my rooms, " +
        "go to kitchen or another saved room, choose room one, repeat directions, end walk, stop camera, Home navigation, or back to navigation."
}

enum class HomeToolAction { LOCAL_AI_SETUP, LEGACY_RECORDINGS, MORE_OPTIONS, FEWER_OPTIONS, START_CAMERA, STOP_CAMERA }
object HomeToolCommands {
    fun resolve(text: String): HomeToolAction? = when (VoicePhrases.normalize(text)) {
        "set up local ai", "setup local ai", "set up ai", "prepare local ai", "install local ai", "set up camera assistant" -> HomeToolAction.LOCAL_AI_SETUP
        "previous room recordings", "open previous room recordings", "old room recordings", "legacy room recordings", "open marker navigation" -> HomeToolAction.LEGACY_RECORDINGS
        "more options", "show more options", "expand options", "assistant options" -> HomeToolAction.MORE_OPTIONS
        "fewer options", "show fewer options", "hide extra options", "collapse options" -> HomeToolAction.FEWER_OPTIONS
        "scan home marker", "scan the home marker", "start home camera", "show home camera", "start camera" -> HomeToolAction.START_CAMERA
        "stop camera", "stop the camera", "close home camera", "stop home camera" -> HomeToolAction.STOP_CAMERA
        else -> null
    }
}

enum class CloudSetupAction { CONNECT, DISCONNECT, INSTRUCTIONS, LICENSE, USE_POLICY, ACCEPT_TERMS, DECLINE_TERMS, CANCEL }
object CloudSetupCommands {
    fun resolve(text: String): CloudSetupAction? = when (VoicePhrases.normalize(text)) {
        "connect and test", "connect and test cloudflare", "test cloudflare", "test connection", "connect cloudflare" -> CloudSetupAction.CONNECT
        "disconnect cloudflare", "disconnect", "use local mode" -> CloudSetupAction.DISCONNECT
        "open setup instructions", "setup instructions", "read setup instructions" -> CloudSetupAction.INSTRUCTIONS
        "license", "open license", "read license", "open the license" -> CloudSetupAction.LICENSE
        "use policy", "open use policy", "acceptable use policy", "open acceptable use policy" -> CloudSetupAction.USE_POLICY
        "i accept the license", "i accept the license and use policy", "accept license and use policy" -> CloudSetupAction.ACCEPT_TERMS
        "i do not accept the license", "uncheck license", "decline the license" -> CloudSetupAction.DECLINE_TERMS
        "cancel connection", "cancel test", "stop the connection test" -> CloudSetupAction.CANCEL
        else -> null
    }
    val help = "Say open setup instructions, open license, open use policy, I accept the license and use policy, " +
        "connect and test, cancel connection, disconnect Cloudflare, read status, or go back. Enter the account ID and API token in their fields; credentials are not read aloud."
}
