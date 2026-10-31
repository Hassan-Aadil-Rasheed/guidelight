package com.guidelight.app.domain

data class NormalizedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height
}

enum class ObstacleRisk { NONE, CAUTION, STOP }
enum class ObstaclePosition { LEFT, CENTER, RIGHT }
enum class GuidanceDirection { STRAIGHT, LEFT, RIGHT }

data class ObstacleAlert(
    val risk: ObstacleRisk,
    val position: ObstaclePosition = ObstaclePosition.CENTER,
    val appearance: ObjectAppearance? = null
)

object ObstacleEvaluator {
    /** Only objects centred in the hidden middle column can trigger automatic cautions. */
    fun evaluate(boxes: List<NormalizedBox>): ObstacleAlert {
        return CameraAlertGrid.focused(boxes).mapNotNull { box ->
            // Small central objects receive neutral descriptions, not a hazard warning.
            if (box.area < .07f) return@mapNotNull null
            val risk = if (box.bottom >= .88f && box.area >= .18f) ObstacleRisk.STOP else ObstacleRisk.CAUTION
            ObstacleAlert(risk, ObstaclePosition.CENTER, ObjectAppearance.from(box)) to box.area
        }.maxWithOrNull(compareBy<Pair<ObstacleAlert, Float>> { it.first.risk.ordinal }.thenBy { it.second })
            ?.first ?: ObstacleAlert(ObstacleRisk.NONE)
    }
    fun corridorCentreX(y: Float, direction: GuidanceDirection): Float {
        val distanceFromFeet = ((1f - y.coerceIn(0f, 1f)) / 0.72f).coerceIn(0f, 1f)
        val bend = distanceFromFeet * distanceFromFeet
        return when (direction) {
            GuidanceDirection.STRAIGHT -> 0.5f
            GuidanceDirection.LEFT -> 0.5f - (0.24f * bend)
            GuidanceDirection.RIGHT -> 0.5f + (0.24f * bend)
        }
    }
}


