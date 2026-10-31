package com.guidelight.app.domain

import java.util.Locale

enum class ObjectHeight { HIGH, MIDDLE, LOW }
enum class ApparentSize { SMALL, MEDIUM, LARGE }

/** Image-space descriptions only: neither physical dimensions nor distance. */
data class ObjectAppearance(val side: ObstaclePosition, val height: ObjectHeight, val size: ApparentSize) {
    fun description(): String {
        val where = when (side) {
            ObstaclePosition.LEFT -> "on your left"
            ObstaclePosition.CENTER -> "ahead"
            ObstaclePosition.RIGHT -> "on your right"
        }
        val vertical = when (height) {
            ObjectHeight.HIGH -> "high in the camera view"
            ObjectHeight.MIDDLE -> "in the middle of the camera view"
            ObjectHeight.LOW -> "low in the camera view"
        }
        return "Object $where, $vertical. Appears ${size.name.lowercase(Locale.ROOT)}."
    }

    companion object {
        fun from(box: NormalizedBox): ObjectAppearance {
            return ObjectAppearance(
                CameraAlertGrid.column(box),
                CameraAlertGrid.row(box),
                when { box.area < .07f -> ApparentSize.SMALL; box.area < .25f -> ApparentSize.MEDIUM; else -> ApparentSize.LARGE }
            )
        }
    }
}

object ObjectDescriptions {
    fun observations(boxes: List<NormalizedBox>): List<ObjectAppearance> = boxes
        .filter(CameraAlertGrid::valid)
        .sortedWith(compareBy<NormalizedBox> { if (CameraAlertGrid.column(it) == ObstaclePosition.CENTER) 0 else 1 }
            .thenByDescending { it.area }).map(ObjectAppearance::from).distinct().take(3)

    fun summary(objects: List<ObjectAppearance>): String = if (objects.isEmpty())
        "No objects detected in this view. Objects may be missed."
    else objects.joinToString(" ") { it.description() }

    fun warning(alert: ObstacleAlert): String {
        val prefix = if (alert.risk == ObstacleRisk.STOP) "Stop. Possible obstacle." else "Caution. Possible obstacle."
        return alert.appearance?.let { "$prefix ${it.description()}" } ?: "$prefix ${when (alert.position) {
            ObstaclePosition.LEFT -> "On your left."; ObstaclePosition.CENTER -> "Ahead."; ObstaclePosition.RIGHT -> "On your right."
        }}"
    }
}

/** Require a stable observation; avoid repeated chatter and never announce a clear path. */
class ObjectAnnouncementGate {
    private var candidate: ObjectAppearance? = null
    private var frames = 0
    private var lastSpoken: ObjectAppearance? = null
    private var lastAt = -20_000L

    fun reset() { candidate = null; frames = 0; lastSpoken = null; lastAt = -20_000L }

    fun offer(objects: List<ObjectAppearance>, now: Long, canSpeak: Boolean): String? {
        val next = objects.firstOrNull()
        frames = if (next != null && next == candidate) frames + 1 else 1
        candidate = next
        if (next == null || frames < 3 || !canSpeak) return null
        val cooldown = if (next == lastSpoken) 20_000 else 8_000
        if (now - lastAt < cooldown) return null
        lastAt = now; lastSpoken = next
        return next.description()
    }
}
