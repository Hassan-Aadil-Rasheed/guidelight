package com.guidelight.app.remote

/** Keep only the latest connection change until it is safe to speak outside dictation. */
class ButtonConnectionCue {
    private var pending: Boolean? = null
    fun changed(connected: Boolean) { pending = connected }
    fun clear() { pending = null }
    fun take(enabled: Boolean, canSpeak: Boolean): String? {
        if (!enabled) { clear(); return null }
        if (!canSpeak) return null
        val connected = pending ?: return null
        clear()
        return if (connected) "GuideLight button connected."
            else "GuideLight button disconnected. Reconnect it or use the microphone."
    }
}
