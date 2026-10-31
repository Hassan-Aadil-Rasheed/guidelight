package com.guidelight.app.indoor

import android.app.Activity
import com.guidelight.app.domain.AppVoiceAction
import com.guidelight.app.domain.AppVoiceCommands
import com.guidelight.app.ui.VoiceNavigation

/** Shared app-level routes for the Home and settings command editors. */
internal object HomeAppVoiceCommands {
    fun handle(
        activity: Activity, text: String, voice: HomeVoiceInput, say: (String) -> Unit,
        help: String, status: () -> String
    ): Boolean {
        val action = AppVoiceCommands.resolve(text) ?: return false
        when (action) {
            AppVoiceAction.HOME -> if (activity is SavedHomeActivity) say("Home navigation. $help") else VoiceNavigation.home(activity)
            AppVoiceAction.NAVIGATE -> VoiceNavigation.main(activity, "navigate")
            AppVoiceAction.CAMERA -> VoiceNavigation.main(activity, "camera")
            AppVoiceAction.HELP -> say(help)
            AppVoiceAction.BACK -> activity.finish()
            AppVoiceAction.CLOSE -> say("Ready. Say what can I say to hear the commands.")
            AppVoiceAction.SELFIE_SETUP -> VoiceNavigation.main(activity, "navigate", buttonSetup = true)
            AppVoiceAction.ENABLE_SELFIE, AppVoiceAction.DISABLE_SELFIE -> {
                val enabled = action == AppVoiceAction.ENABLE_SELFIE
                voice.setRemoteEnabled(enabled)
                say(if (enabled) "GuideLight button enabled. Your setting is saved." else "GuideLight button disabled. Your setting is saved.")
            }
            AppVoiceAction.STATUS -> say(status())
            AppVoiceAction.CANCEL_INPUT -> say("Voice command cancelled.")
            else -> if (!VoiceNavigation.external(activity, action)) say("Say back to navigation to search or change a destination.")
        }
        return true
    }
}
