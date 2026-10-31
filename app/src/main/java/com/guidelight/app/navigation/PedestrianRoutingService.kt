package com.guidelight.app.navigation

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class PedestrianRoutingService(
    private val baseUrl: String = "https://valhalla1.openstreetmap.de"
) {
    suspend fun route(origin: GeoPoint, destination: GeoPoint): WalkingRoute {
        val request = JSONObject().apply {
            put("locations", JSONArray().apply {
                put(JSONObject().put("lat", origin.latitude).put("lon", origin.longitude))
                put(JSONObject().put("lat", destination.latitude).put("lon", destination.longitude))
            })
            put("costing", "pedestrian")
            put("costing_options", JSONObject().put("pedestrian", JSONObject()
                .put("type", "blind")
                .put("use_ferry", 0.0)
                .put("walking_speed", 4.0)))
            put("units", "kilometers")
            put("language", "en-US")
            put("directions_type", "instructions")
        }
        val encoded = URLEncoder.encode(request.toString(), Charsets.UTF_8.name())
        return parseRoute(GuideHttp.get("$baseUrl/route?json=$encoded"))
    }

    internal fun parseRoute(json: String): WalkingRoute {
        val trip = JSONObject(json).getJSONObject("trip")
        val summary = trip.getJSONObject("summary")
        val leg = trip.getJSONArray("legs").getJSONObject(0)
        val shape = decodePolyline6(leg.getString("shape"))
        check(shape.isNotEmpty()) { "Route has no path" }
        val rawManeuvers = leg.getJSONArray("maneuvers")
        val maneuvers = buildList {
            for (index in 0 until rawManeuvers.length()) {
                val maneuver = rawManeuvers.getJSONObject(index)
                val shapeIndex = maneuver.optInt("begin_shape_index", 0).coerceIn(shape.indices)
                val instruction = maneuver.optString("verbal_pre_transition_instruction")
                    .ifBlank { maneuver.optString("instruction", "Continue walking") }
                add(RouteManeuver(instruction, shape[shapeIndex], index == rawManeuvers.length() - 1,
                    shapeIndex, maneuver.optInt("type", 0)))
            }
        }
        return WalkingRoute(
            maneuvers = maneuvers,
            distanceKilometres = summary.getDouble("length"),
            durationSeconds = summary.getInt("time"),
            shape = shape
        )
    }

    internal fun decodePolyline6(encoded: String): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        var index = 0
        var latitude = 0
        var longitude = 0
        while (index < encoded.length) {
            fun nextDelta(): Int {
                var result = 0
                var shift = 0
                var value: Int
                do {
                    value = encoded[index++].code - 63
                    result = result or ((value and 0x1f) shl shift)
                    shift += 5
                } while (value >= 0x20 && index < encoded.length)
                return if (result and 1 != 0) (result shr 1).inv() else result shr 1
            }
            latitude += nextDelta()
            longitude += nextDelta()
            points += GeoPoint(latitude / 1e6, longitude / 1e6)
        }
        return points
    }
}
