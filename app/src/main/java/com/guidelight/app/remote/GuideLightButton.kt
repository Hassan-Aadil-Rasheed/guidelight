package com.guidelight.app.remote

import java.util.Locale

/** Bluetooth aliases can change while Android still reports the factory HID name. */
object GuideLightButton {
    fun matches(deviceName: String?): Boolean = deviceName?.trim()
        ?.replace(Regex("\\s+"), " ")?.lowercase(Locale.ROOT) in
        setOf("guidelight button", "guidelight", "selfie")
}
