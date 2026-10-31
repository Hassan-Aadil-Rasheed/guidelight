package com.guidelight.app.navigation

/** Use monotonic time so clock changes cannot make old fixes appear live. */
data class LocationFix(val point: GeoPoint, val accuracy: Float, val elapsedMillis: Long)

fun acceptsLocationFix(current: LocationFix?, candidate: LocationFix, now: Long): Boolean {
    if (!candidate.point.latitude.isFinite() || candidate.point.latitude !in -90.0..90.0 ||
        !candidate.point.longitude.isFinite() || candidate.point.longitude !in -180.0..180.0 ||
        !candidate.accuracy.isFinite() || candidate.accuracy <= 0 ||
        now - candidate.elapsedMillis !in 0..120_000) return false
    if (current == null) return true
    if (candidate.elapsedMillis < current.elapsedMillis) return false
    if (candidate.elapsedMillis == current.elapsedMillis && candidate.accuracy >= current.accuracy) return false
    // A network callback must not displace a still-fresh, precise walking fix.
    if (now - current.elapsedMillis in 0..10_000 && current.accuracy <= 25f && candidate.accuracy > 25f) return false
    return true
}

data class WalkingPosition(
    val point: GeoPoint,
    val accuracyMetres: Float,
    val stale: Boolean,
    val heading: Double? = null
) {
    val precise: Boolean get() = !stale && accuracyMetres <= 25f
    val label: String get() = when {
        stale -> "Last known location"
        !precise -> "Approximate location"
        else -> "Live location"
    }
}
