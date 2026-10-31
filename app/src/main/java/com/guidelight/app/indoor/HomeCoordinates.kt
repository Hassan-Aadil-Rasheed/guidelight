package com.guidelight.app.indoor

import com.google.ar.core.Pose
import kotlin.math.hypot

/** Gravity supplies vertical; the fixed image's right axis supplies a repeatable horizontal axis.
 * Never use ARCore session coordinates directly for saved rooms: they change after reopening.
 */
object HomeCoordinates {
    fun point(marker: Pose, camera: Pose): HomePoint? {
        val right = marker.rotateVector(floatArrayOf(1f, 0f, 0f))
        val length = hypot(right[0], right[2])
        if (length < .7f) return null
        val rx = right[0] / length; val rz = right[2] / length
        val dx = camera.tx() - marker.tx(); val dz = camera.tz() - marker.tz()
        return HomePoint(dx * rx + dz * rz, camera.ty() - marker.ty(), -dx * rz + dz * rx).takeIf { it.valid() }
    }

    fun forward(marker: Pose, camera: Pose): Pair<Float, Float> {
        val right = marker.rotateVector(floatArrayOf(1f, 0f, 0f))
        val length = hypot(right[0], right[2]).coerceAtLeast(.001f)
        val forward = camera.rotateVector(floatArrayOf(0f, 0f, -1f))
        return (forward[0] * right[0] + forward[2] * right[2]) / length to
            (-forward[0] * right[2] + forward[2] * right[0]) / length
    }
}
