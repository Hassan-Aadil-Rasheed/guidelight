package com.guidelight.app.domain

/** One output path for every GuideLight utterance, including camera warnings. */
class GuidanceSpeech(
    private val isDictating: () -> Boolean,
    private val stopPlayback: () -> Unit,
    private val output: (text: String, append: Boolean) -> Unit
) {
    fun silence() = stopPlayback()

    fun say(text: String, append: Boolean = false) {
        // Drop muted speech rather than queueing obsolete warnings for later.
        if (!isDictating()) output(text, append)
    }

    fun sequence(messages: List<String>) {
        messages.forEachIndexed { index, message -> say(message, append = index > 0) }
    }
}
