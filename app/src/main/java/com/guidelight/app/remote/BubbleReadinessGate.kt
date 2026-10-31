package com.guidelight.app.remote

/** Wait for an editable target and a settled overlay after keyboard/layout changes. */
class BubbleReadinessGate(private val settleMillis: Long = 400) {
    private var candidate: BubbleWindow? = null
    private var stableSince = 0L

    fun ready(window: BubbleWindow?, editableFocused: Boolean, now: Long): Boolean {
        if (window == null || !editableFocused) {
            clear()
            return false
        }
        if (candidate != window) {
            candidate = window
            stableSince = now
            return false
        }
        return now - stableSince >= settleMillis
    }

    fun clear() { candidate = null }
}
