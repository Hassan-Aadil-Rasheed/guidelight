package com.guidelight.app.domain

/** An internal 3x3 image partition. No grid is rendered or exposed to TalkBack. */
object CameraAlertGrid {
    private const val THIRD = 1f / 3f

    fun valid(box: NormalizedBox): Boolean =
        listOf(box.left, box.top, box.right, box.bottom).all(Float::isFinite) &&
            box.left >= 0f && box.top >= 0f && box.right <= 1f && box.bottom <= 1f &&
            box.width > 0f && box.height > 0f && box.area >= .004f

    fun column(box: NormalizedBox): ObstaclePosition = when (val x = (box.left + box.right) / 2f) {
        in THIRD..(2f * THIRD) -> ObstaclePosition.CENTER
        else -> if (x < THIRD) ObstaclePosition.LEFT else ObstaclePosition.RIGHT
    }

    fun row(box: NormalizedBox): ObjectHeight = when (val y = (box.top + box.bottom) / 2f) {
        in THIRD..(2f * THIRD) -> ObjectHeight.MIDDLE
        else -> if (y < THIRD) ObjectHeight.HIGH else ObjectHeight.LOW
    }

    fun focused(boxes: List<NormalizedBox>): List<NormalizedBox> = boxes.filter {
        valid(it) && column(it) == ObstaclePosition.CENTER
    }
}

/** Stable warnings, with a longer repeat interval and a brief clear-frame grace period. */
class ObstacleAlertGate {
    private data class Key(val risk: ObstacleRisk, val height: ObjectHeight?)
    private var candidate: Key? = null
    private var candidateAt = 0L
    private var frames = 0
    private var absentAt: Long? = null
    private var visible = false
    private var lastKey: Key? = null
    private var lastAt = -30_000L

    fun reset() {
        candidate = null; frames = 0; absentAt = null; visible = false
        lastKey = null; lastAt = -30_000L
    }

    fun offer(alert: ObstacleAlert, now: Long): ObstacleAlert? {
        if (alert.risk == ObstacleRisk.NONE) {
            candidate = null; frames = 0
            val since = absentAt ?: now.also { absentAt = it }
            if (now - since >= 750) {
                lastKey = null
                if (visible) { visible = false; return alert }
            }
            return null
        }
        absentAt = null
        val key = Key(alert.risk, alert.appearance?.height)
        if (candidate != key) { candidate = key; candidateAt = now; frames = 0 }
        frames++
        if (frames < 3 || now - candidateAt < 500) return null
        val escalated = alert.risk.ordinal > (lastKey?.risk?.ordinal ?: 0) && lastKey != null
        val delay = if (key == lastKey) 30_000 else 8_000
        if (!escalated && now - lastAt < delay) return null
        lastAt = now; lastKey = key; visible = true
        return alert
    }
}
