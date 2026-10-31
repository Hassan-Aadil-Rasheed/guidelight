package com.guidelight.app.navigation

import com.guidelight.app.domain.GuidanceDirection
import kotlin.math.*

/** Progress follows route geometry, not straight-line distance to a corner. */
class NavigationSession(private val route: WalkingRoute) {
    private val shape = route.shape
    private val lengths = shape.zipWithNext { a, b -> a.distanceTo(b).toDouble() }
    private val cumulative = listOf(0.0) + lengths.runningFold(0.0, Double::plus).drop(1)
    private var progress = 0.0
    private var started = false
    private var lastKey = ""
    private var lastAnnouncement = Long.MIN_VALUE / 2

    fun startingInstruction() = "Hold the phone upright with its rear camera facing ahead. Checking your direction."

    fun update(point: GeoPoint, heading: Double? = null, accuracy: Float = 10f,
               fresh: Boolean = true, now: Long = System.currentTimeMillis()): NavigationUpdate {
        if (!fresh || !accuracy.isFinite() || accuracy > 25f || accuracy <= 0f)
            return result("Updating location. Turn guidance paused.", "gps", now,
                remaining = (cumulative.last() - progress).coerceAtLeast(0.0), guidanceAvailable = false)
        if (shape.size < 2) return result("Route unavailable. Choose the destination again.", "route", now, guidanceAvailable = false)
        val candidates = lengths.indices.filter { cumulative[it + 1] >= progress - 15 && cumulative[it] <= progress + 80 }
        val match = candidates.map { i ->
            val a = shape[i]; val b = shape[i + 1]
            val scaleX = 111_195 * cos(Math.toRadians(a.latitude))
            val bx = (b.longitude - a.longitude) * scaleX; val by = (b.latitude - a.latitude) * 111_195
            val px = (point.longitude - a.longitude) * scaleX; val py = (point.latitude - a.latitude) * 111_195
            val t = if (bx * bx + by * by < .001) 0.0 else ((px * bx + py * by) / (bx * bx + by * by)).coerceIn(0.0, 1.0)
            Triple(i, t, hypot(px - bx * t, py - by * t))
        }.minByOrNull { it.third } ?: return result("Pause. The route could not be located.", "route", now, guidanceAvailable = false)
        if (match.third > max(25.0, accuracy * 1.5))
            return result("You may be off the route. Pause and check your position with help.", "off-route", now, guidanceAvailable = false)
        val projected = cumulative[match.first] + lengths[match.first] * match.second
        progress = max(progress, projected)
        val remaining = (cumulative.last() - progress).coerceAtLeast(0.0)
        if (remaining <= 12 && point.distanceTo(shape.last()) <= 15 && accuracy <= 15)
            return result("You are near your destination. Check the entrance with help.", "arrival", now, arrived = true, remaining = remaining)
        val next = route.maneuvers.withIndex().firstOrNull { (i, m) ->
            i > 0 && cumulative[m.shapeIndex.coerceIn(cumulative.indices)] >= progress - 6
        }
        val turnAt = next?.value?.shapeIndex?.coerceIn(cumulative.indices)
        val distance = turnAt?.let { (cumulative[it] - progress).coerceAtLeast(0.0) } ?: remaining
        val target = pointAt(min(progress + 8.0, cumulative.last()))
        val bearing = pointAt(progress).bearingTo(target)
        if (heading == null || !heading.isFinite()) return result(startingInstruction(), "heading", now, remaining = remaining, guidanceAvailable = false)
        val delta = relativeAngle(bearing, heading)
        val alignment = when {
            abs(delta) > 140 -> "Turn around to face the route."
            delta < -30 -> "Turn left to face the route."
            delta > 30 -> "Turn right to face the route."
            else -> null
        }
        val maneuver = next?.value
        val action = maneuver?.let { simpleAction(it) } ?: "Continue straight"
        val rounded = (round(distance / 5) * 5).toInt().coerceAtLeast(5)
        val approach = when {
            maneuver?.isArrival == true -> "Continue straight for about $rounded metres to your destination."
            distance <= 10 -> "$action now."
            else -> "In about $rounded metres, ${action.replaceFirstChar(Char::lowercase)}."
        }
        val stage = when { distance <= 10 -> 0; distance <= 20 -> 1; distance <= 50 -> 2; distance <= 100 -> 3; else -> 4 }
        val text = if (alignment != null && (!started || distance > 15)) "$alignment $approach"
            else if (distance > 10) "Continue straight. $approach" else approach
        if (alignment == null) started = true
        val direction = when {
            alignment != null && delta < -30 && abs(delta) <= 140 -> GuidanceDirection.LEFT
            alignment != null && delta > 30 && abs(delta) <= 140 -> GuidanceDirection.RIGHT
            distance <= 10 && action.contains("left", true) -> GuidanceDirection.LEFT
            distance <= 10 && action.contains("right", true) -> GuidanceDirection.RIGHT
            else -> GuidanceDirection.STRAIGHT
        }
        val key = "${next?.index}:$stage:${if (alignment != null) direction.name + (abs(delta) > 140) else "aligned"}"
        val origin = pointAt(progress)
        val radians = Math.toRadians(heading)
        val preview = (0..24).mapNotNull { step ->
            val at = progress + .6 + step * .14
            if (at > cumulative.last()) null else {
                val p = pointAt(at)
                val east = (p.longitude - origin.longitude) * 111_195 * cos(Math.toRadians(origin.latitude))
                val north = (p.latitude - origin.latitude) * 111_195
                (east * cos(radians) - north * sin(radians)).toFloat() to
                    (east * sin(radians) + north * cos(radians)).toFloat()
            }
        }
        return result(text, key, now, remaining = remaining, direction = direction,
            relativeBearing = if (abs(delta) <= 85) delta else null,
            arrowBearing = when { alignment != null -> delta; direction == GuidanceDirection.LEFT -> -90.0;
                direction == GuidanceDirection.RIGHT -> 90.0; else -> 0.0 },
            preview = if (abs(delta) <= 85) preview else emptyList())
    }

    private fun pointAt(metres: Double): GeoPoint {
        val i = lengths.indices.firstOrNull { cumulative[it + 1] >= metres } ?: lengths.lastIndex
        val t = if (lengths[i] < .001) 0.0 else ((metres - cumulative[i]) / lengths[i]).coerceIn(0.0, 1.0)
        return GeoPoint(shape[i].latitude + t * (shape[i + 1].latitude - shape[i].latitude),
            shape[i].longitude + t * (shape[i + 1].longitude - shape[i].longitude))
    }

    private fun simpleAction(m: RouteManeuver): String = when {
        m.type in listOf(9, 10) || m.instruction.contains("u-turn", true) -> "Turn around"
        m.type in listOf(11, 12, 13) || m.instruction.contains("left", true) -> "Turn left"
        m.type in listOf(6, 7, 8) || m.instruction.contains("right", true) -> "Turn right"
        else -> "Continue straight"
    }

    private fun result(text: String, key: String, now: Long, arrived: Boolean = false,
                       remaining: Double = (cumulative.last() - progress).coerceAtLeast(0.0), direction: GuidanceDirection = GuidanceDirection.STRAIGHT,
                       relativeBearing: Double? = null, preview: List<Pair<Float, Float>> = emptyList(),
                       guidanceAvailable: Boolean = true, arrowBearing: Double? = null): NavigationUpdate {
        val changed = key != lastKey
        val speak = (changed && (!guidanceAvailable || now - lastAnnouncement >= 5_000)) ||
            (guidanceAvailable && now - lastAnnouncement >= 30_000) || arrived
        if (speak) { lastKey = key; lastAnnouncement = now }
        val seconds = (route.durationSeconds * remaining / cumulative.last().coerceAtLeast(1.0)).roundToInt()
        return NavigationUpdate(text, arrived, direction, remaining, relativeBearing, if (speak) text else null, preview, guidanceAvailable, seconds, arrowBearing)
    }
}

fun relativeAngle(target: Double, heading: Double): Double = ((target - heading + 540) % 360 + 360) % 360 - 180

data class NavigationUpdate(val instruction: String, val arrived: Boolean,
    val direction: GuidanceDirection = GuidanceDirection.STRAIGHT, val remainingMetres: Double = 0.0,
    val relativeBearing: Double? = null, val announcement: String? = null,
    val groundPreview: List<Pair<Float, Float>> = emptyList(), val guidanceAvailable: Boolean = true,
    val remainingSeconds: Int = 0, val arrowBearing: Double? = null)
