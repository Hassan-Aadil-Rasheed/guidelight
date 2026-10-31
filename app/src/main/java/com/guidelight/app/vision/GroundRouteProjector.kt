package com.guidelight.app.vision

import android.opengl.Matrix
import com.google.ar.core.*
import kotlin.math.*

data class GroundRibbon(val centre: List<Pair<Float, Float>>, val polygon: List<Pair<Float, Float>>)

/** Projects only onto a currently tracked horizontal surface; never substitutes a screen sticker. */
class GroundRouteProjector {
    fun project(frame: Frame, width: Int, height: Int, relativeBearing: Double?, preview: List<Pair<Float, Float>>): GroundRibbon? {
        if (width == 0 || height == 0 || relativeBearing == null || abs(relativeBearing) > 85 ||
            frame.camera.trackingState != TrackingState.TRACKING) return null
        val camera = frame.camera
        val cameraPosition = camera.pose.translation
        val plane = listOf(.50f to .85f, .35f to .8f, .65f to .8f).asSequence().flatMap { (x, y) ->
            frame.hitTest(x * width, y * height).asSequence()
        }.mapNotNull { hit ->
            (hit.trackable as? Plane)?.takeIf {
                it.type == Plane.Type.HORIZONTAL_UPWARD_FACING && it.trackingState == TrackingState.TRACKING &&
                    it.subsumedBy == null && it.isPoseInPolygon(hit.hitPose) &&
                    cameraPosition[1] - it.centerPose.ty() in 1.0f..2.2f
            }
        }.firstOrNull() ?: return null
        val forward = camera.pose.rotateVector(floatArrayOf(0f, 0f, -1f))
        val norm = hypot(forward[0], forward[2])
        if (norm < .45f) return null
        val fx = forward[0] / norm; val fz = forward[2] / norm
        val view = FloatArray(16); val projection = FloatArray(16); val vp = FloatArray(16)
        camera.getViewMatrix(view, 0); camera.getProjectionMatrix(projection, 0, .1f, 30f)
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
        fun screen(x: Float, y: Float, z: Float): Pair<Float, Float>? {
            val clip = FloatArray(4)
            Matrix.multiplyMV(clip, 0, vp, 0, floatArrayOf(x, y, z, 1f), 0)
            if (clip[3] <= .1f) return null
            val nx = (clip[0] / clip[3] + 1) / 2
            val ny = (1 - clip[1] / clip[3]) / 2
            return (nx to ny).takeIf { nx in -.15f..1.15f && ny in -.15f..1.15f }
        }
        val centres = mutableListOf<Pair<Float, Float>>()
        val left = mutableListOf<Pair<Float, Float>>(); val right = mutableListOf<Pair<Float, Float>>()
        for ((index, offset) in preview.withIndex()) {
            val next = preview.getOrNull(index + 1) ?: offset
            val previous = preview.getOrNull(index - 1) ?: offset
            val dr = next.first - previous.first; val df = next.second - previous.second
            val tangentLength = hypot(dr, df)
            if (offset.second <= .1f || tangentLength < .001f) continue
            val dx = (-fz * dr + fx * df) / tangentLength
            val dz = (fx * dr + fz * df) / tangentLength
            val x = cameraPosition[0] - fz * offset.first + fx * offset.second; val y = plane.centerPose.ty() + .015f
            val z = cameraPosition[2] + fx * offset.first + fz * offset.second
            val half = .25f
            val lx = x + dz * half; val lz = z - dx * half
            val rx = x - dz * half; val rz = z + dx * half
            if (!plane.isPoseInPolygon(Pose.makeTranslation(lx, y, lz)) ||
                !plane.isPoseInPolygon(Pose.makeTranslation(rx, y, rz))) {
                if (centres.isNotEmpty()) break else continue
            }
            val c = screen(x, y, z); val l = screen(lx, y, lz); val r = screen(rx, y, rz)
            if (c == null || l == null || r == null) { if (centres.isNotEmpty()) break else continue }
            centres += c; left += l; right += r
        }
        return if (centres.size >= 3) GroundRibbon(centres, left + right.asReversed()) else null
    }
}
