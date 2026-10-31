package com.guidelight.app.domain

/** Remember that a current response needs reading, never queue an obsolete warning or turn. */
class DeferredSpeech {
    private var pending = false
    fun allow(ready: Boolean, foreground: Boolean): Boolean {
        if (!ready || !foreground) { pending = true; return false }
        pending = false
        return true
    }
    fun defer() { pending = true }
    fun clear() { pending = false }
    fun take(ready: Boolean, foreground: Boolean, dictating: Boolean): Boolean {
        if (!pending || !ready || !foreground || dictating) return false
        pending = false
        return true
    }
}
