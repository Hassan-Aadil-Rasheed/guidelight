package com.guidelight.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.guidelight.app.MainActivity
import com.guidelight.app.domain.AppVoiceAction
import com.guidelight.app.indoor.SavedHomeActivity
import com.guidelight.app.vision.CloudflareSetupActivity

object VoiceNavigation {
    const val SCREEN = "com.guidelight.app.VOICE_SCREEN"
    const val BUTTON_SETUP = "com.guidelight.app.VOICE_BUTTON_SETUP"
    fun main(activity: Activity, screen: String, buttonSetup: Boolean = false) {
        activity.startActivity(Intent(activity, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(SCREEN, screen).putExtra(BUTTON_SETUP, buttonSetup))
    }
    fun home(activity: Activity) {
        activity.startActivity(Intent(activity, SavedHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
    fun external(activity: Activity, action: AppVoiceAction): Boolean {
        val intent = when (action) {
            AppVoiceAction.ACCESSIBILITY_SETTINGS -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            AppVoiceAction.APP_SETTINGS -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
            AppVoiceAction.LOCATION_SETTINGS -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            AppVoiceAction.CLOUD_SETTINGS -> Intent(activity, CloudflareSetupActivity::class.java)
            AppVoiceAction.PRIVACY -> Intent(Intent.ACTION_VIEW, Uri.parse("https://policies.google.com/privacy"))
            else -> return false
        }
        activity.startActivity(intent)
        return true
    }
}
