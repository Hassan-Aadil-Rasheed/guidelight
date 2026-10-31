package com.guidelight.app.navigation

import kotlin.math.*
import java.util.Locale

data class GeoPoint(val latitude: Double, val longitude: Double) {
    fun distanceTo(other: GeoPoint): Float {
        val lat1 = Math.toRadians(latitude); val lat2 = Math.toRadians(other.latitude)
        val a = sin((lat2 - lat1) / 2).pow(2) + cos(lat1) * cos(lat2) *
            sin(Math.toRadians(other.longitude - longitude) / 2).pow(2)
        return (6_371_000 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))).toFloat()
    }

    fun bearingTo(other: GeoPoint): Double {
        val a = Math.toRadians(latitude); val b = Math.toRadians(other.latitude)
        val d = Math.toRadians(other.longitude - longitude)
        return (Math.toDegrees(atan2(sin(d) * cos(b), cos(a) * sin(b) - sin(a) * cos(b) * cos(d))) + 360) % 360
    }
}

data class PlaceResult(
    val name: String,
    val address: String,
    val point: GeoPoint,
    val distanceMetres: Int
) {
    fun spokenLabel(): String {
        val distance = if (distanceMetres < 1_000) {
            "$distanceMetres metres away"
        } else {
            String.format(Locale.US, "%.1f kilometres away", distanceMetres / 1_000.0)
        }
        return "$name, $distance. $address"
    }
}


data class RouteManeuver(
    val instruction: String,
    val point: GeoPoint,
    val isArrival: Boolean,
    val shapeIndex: Int = 0,
    val type: Int = 0
)

data class WalkingRoute(
    val maneuvers: List<RouteManeuver>,
    val distanceKilometres: Double,
    val durationSeconds: Int,
    val shape: List<GeoPoint> = maneuvers.map { it.point }
)
