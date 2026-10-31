package com.guidelight.app.navigation

import android.content.Context
import android.hardware.*
import android.location.Location
import android.os.SystemClock
import kotlin.math.*

/** Bearing of the rear camera (-device Z), rather than the top edge of a flat phone. */
class CameraHeading(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private var value: Double? = null
    private var at = 0L
    private var accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var errorRadians: Float? = null
    private var declination = 0.0
    private val matrix = FloatArray(9)

    fun start() {
        value = null; at = 0; accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
        manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }
    fun stop() { manager.unregisterListener(this); value = null }
    fun locate(location: Location) {
        declination = GeomagneticField(location.latitude.toFloat(), location.longitude.toFloat(),
            location.altitude.toFloat(), System.currentTimeMillis()).declination.toDouble()
    }
    fun current(): Double? = value?.takeIf {
        SystemClock.elapsedRealtime() - at <= 2500 && accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM &&
            (errorRadians == null || errorRadians!! in 0f..Math.toRadians(25.0).toFloat())
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { this.accuracy = accuracy }
    override fun onSensorChanged(event: SensorEvent) {
        accuracy = event.accuracy
        errorRadians = event.values.getOrNull(4)?.takeIf { it >= 0f }
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val raw = rearCameraBearing(matrix) ?: run { value = null; return }
        val trueBearing = (raw + declination + 360) % 360
        value = value?.let { (it + .18 * relativeAngle(trueBearing, it) + 360) % 360 } ?: trueBearing
        at = SystemClock.elapsedRealtime()
    }
}

/** Rotation is row-major device -> East/North/Up; a downward camera has no reliable horizontal heading. */
fun rearCameraBearing(rotation: FloatArray): Double? {
    val east = -rotation[2].toDouble(); val north = -rotation[5].toDouble()
    if (hypot(east, north) < .45) return null
    return (Math.toDegrees(atan2(east, north)) + 360) % 360
}
