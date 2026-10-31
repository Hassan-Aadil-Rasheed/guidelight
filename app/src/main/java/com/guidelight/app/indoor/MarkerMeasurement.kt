package com.guidelight.app.indoor

import kotlin.math.sqrt

/** Displacement of the phone from its scan position, in the fixed image anchor's coordinates.
 * This is neither distance walked nor distance to a room. Never retain it after tracking loss.
 */
class MarkerMeasurement {
    private var start: FloatArray? = null

    fun begin(position: FloatArray) {
        require(position.size == 3 && position.all { it.isFinite() })
        start = position.copyOf()
    }

    fun clear() { start = null }

    fun displacement(position: FloatArray): Float? {
        val origin = start ?: return null
        if (position.size != 3 || position.any { !it.isFinite() }) return null
        return sqrt(position.indices.sumOf { i ->
            val delta = (position[i] - origin[i]).toDouble()
            delta * delta
        }).toFloat()
    }
}
