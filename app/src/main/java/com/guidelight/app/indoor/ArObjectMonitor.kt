package com.guidelight.app.indoor

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.google.ar.core.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.guidelight.app.domain.*

/** ARCore owns the camera. ML Kit consumes its CPU image; no second CameraX session is opened. */
class ArObjectMonitor(
    private val context: Context,
    private val onBoxes: (List<NormalizedBox>) -> Unit,
    private val onRisk: (ObstacleAlert) -> Unit,
    private val onAvailability: (Boolean) -> Unit
) {
    // ARCore images are sampled, rather than delivered as a continuous video
    // stream. Complete detection on each sample; no tracking IDs are consumed.
    private val detector = ObjectDetection.getClient(ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE).enableMultipleObjects().build())
    private var busy = false
    private var closed = false
    private var generation = 0
    private var lastStart = 0L
    private var lastSuccess = 0L
    private var lastDiagnosticAt = 0L
    private val alertGate = ObstacleAlertGate()

    fun offer(session: Session, frame: Frame, displayRotation: Int) {
        if (closed) return
        val now = SystemClock.elapsedRealtime()
        if (lastSuccess != 0L && now - lastSuccess > 1800L) {
            onBoxes(emptyList()); onAvailability(false); onRisk(ObstacleAlert(ObstacleRisk.NONE))
            lastSuccess = 0L; alertGate.reset()
        }
        if (busy || now - lastStart < 350) return
        val image = try { frame.acquireCameraImage() } catch (_: Exception) { return }
        val width = image.width.toFloat(); val height = image.height.toFloat()
        try {
            val sensor = context.getSystemService(CameraManager::class.java)
                .getCameraCharacteristics(session.cameraConfig.cameraId).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val device = when (displayRotation) { Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0 }
            val rotation = (sensor - device + 360) % 360
            val basis = FloatArray(6)
            frame.transformCoordinates2d(Coordinates2d.IMAGE_PIXELS,
                floatArrayOf(0f, 0f, width, 0f, 0f, height), Coordinates2d.VIEW_NORMALIZED, basis)
            val token = generation
            busy = true; lastStart = now
            detector.process(InputImage.fromMediaImage(image, rotation))
                .addOnSuccessListener { objects ->
                    if (closed || generation != token || SystemClock.elapsedRealtime() - now > 1200L) return@addOnSuccessListener
                    lastSuccess = SystemClock.elapsedRealtime()
                    onAvailability(true)
                    val boxes = objects.mapNotNull { item ->
                        val r = item.boundingBox
                        ArBoxMapping.map(floatArrayOf(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()),
                            rotation, width, height, basis)
                    }
                    if (com.guidelight.app.BuildConfig.DEBUG && now - lastDiagnosticAt >= 5_000) {
                        lastDiagnosticAt = now
                        Log.i("GuideObjects", "AR image=${width.toInt()}x${height.toInt()} rotation=$rotation detected=${objects.size} mapped=${boxes.size} transform=${basis.joinToString()}")
                    }
                    onBoxes(boxes)
                    alertGate.offer(ObstacleEvaluator.evaluate(boxes), SystemClock.elapsedRealtime())?.let(onRisk)
                }.addOnFailureListener {
                    if (!closed && token == generation) { onAvailability(false); onBoxes(emptyList()); alertGate.reset() }
                }.addOnCompleteListener { image.close(); busy = false }
        } catch (_: Exception) { image.close(); busy = false; onAvailability(false) }
    }

    fun invalidate() {
        generation++; alertGate.reset(); lastSuccess = 0L
        onBoxes(emptyList()); onRisk(ObstacleAlert(ObstacleRisk.NONE)); onAvailability(false)
    }
    fun close() { if (!closed) { invalidate(); closed = true; detector.close() } }
}

object ArBoxMapping {
    /** Undo ML Kit's input rotation, then apply ARCore's crop/display transform. */
    fun map(rect: FloatArray, rotation: Int, width: Float, height: Float, basis: FloatArray): NormalizedBox? {
        val corners = listOf(rect[0] to rect[1], rect[2] to rect[1], rect[2] to rect[3], rect[0] to rect[3]).map { (x, y) ->
            val raw = when (rotation) {
                90 -> y to height - x
                180 -> width - x to height - y
                270 -> width - y to x
                else -> x to y
            }
            val u = raw.first / width; val v = raw.second / height
            basis[0] + u * (basis[2] - basis[0]) + v * (basis[4] - basis[0]) to
                basis[1] + u * (basis[3] - basis[1]) + v * (basis[5] - basis[1])
        }
        if (corners.any { !it.first.isFinite() || !it.second.isFinite() }) return null
        val left = corners.minOf { it.first }.coerceIn(0f, 1f); val right = corners.maxOf { it.first }.coerceIn(0f, 1f)
        val top = corners.minOf { it.second }.coerceIn(0f, 1f); val bottom = corners.maxOf { it.second }.coerceIn(0f, 1f)
        return if (right > left && bottom > top) NormalizedBox(left, top, right, bottom) else null
    }
}
