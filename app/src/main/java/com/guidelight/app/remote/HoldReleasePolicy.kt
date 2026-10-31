package com.guidelight.app.remote

/** Continued stationary strokes can complete before their nominal duration. Use real time. */
object HoldReleasePolicy {
    enum class Action { WAIT, HELD, RELEASE }
    fun action(contactElapsed: Long, sessionElapsed: Long, releaseRequested: Boolean, minimumContact: Long): Action = when {
        contactElapsed < minimumContact -> Action.WAIT
        releaseRequested || sessionElapsed >= 30_000 -> Action.RELEASE
        else -> Action.HELD
    }
}
