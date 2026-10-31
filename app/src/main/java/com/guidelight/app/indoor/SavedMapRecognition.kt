package com.guidelight.app.indoor

import kotlin.math.sqrt

data class PlaceMatch(val node: Int, val inliers: Int, val matches: Int, val rejected: Int,
    val hypothesis: Float, val x: Float, val y: Float, val z: Float)

/** Recognition evidence only. It never authorizes walking or invents a room label. */
class SavedMapRecognition {
    private var previous: PlaceMatch? = null
    private var previousAt = 0L
    private var successes = 0
    private var matchedAt = Long.MIN_VALUE / 2
    private var lastEvidenceAt = Long.MIN_VALUE / 2

    fun observe(match: PlaceMatch, now: Long): Boolean {
        if (match.node <= 0 || match.inliers < 30 || match.matches < 30 || match.rejected != 0 ||
            match.hypothesis < .2f || listOf(match.x, match.y, match.z, match.hypothesis).any { !it.isFinite() }) {
            return recognized(now)
        }
        // Multiple draws of one native result are not independent recognition events.
        if (now - lastEvidenceAt < 600) return recognized(now)
        val old = previous
        val distance = old?.let { sqrt((it.x-match.x)*(it.x-match.x) + (it.y-match.y)*(it.y-match.y) + (it.z-match.z)*(it.z-match.z)) }
        val consistent = old != null && now - previousAt <= 2500 && distance != null && distance <= .8f
        successes = if (consistent) successes + 1 else 1
        if (!consistent) matchedAt = Long.MIN_VALUE / 2
        previous = match; previousAt = now; lastEvidenceAt = now
        if (successes >= 3) matchedAt = now
        return recognized(now)
    }
    fun recognized(now: Long) = now - matchedAt in 0..3000
    fun verifiedNode(now: Long): Int? = if (recognized(now)) previous?.node else null
    fun reset() { previous = null; successes = 0; previousAt = 0; matchedAt = Long.MIN_VALUE / 2; lastEvidenceAt = Long.MIN_VALUE / 2 }
}
