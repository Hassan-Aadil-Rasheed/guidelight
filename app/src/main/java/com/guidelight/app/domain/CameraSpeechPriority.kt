package com.guidelight.app.domain

/** Camera utterances finish before the next navigation update replaces them. */
class CameraSpeechPriority {
    private var utterance: String? = null
    private var expiresAt = 0L

    fun started(id: String, now: Long) { utterance = id; expiresAt = now + 30_000 }
    fun finished(id: String?) { if (utterance == id) utterance = null }
    fun clear() { utterance = null }
    fun blocksNavigation(now: Long): Boolean {
        if (now >= expiresAt) utterance = null
        return utterance != null
    }
}
