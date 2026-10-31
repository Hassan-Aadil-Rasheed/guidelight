package com.guidelight.app.navigation

import kotlinx.serialization.json.*
import java.io.IOException

/** Decodes category results locally. This class makes no network requests. */
internal object NearbyPlaceResults {
    fun parse(json: String, category: NearbyCategory, origin: GeoPoint, radius: Int): List<PlaceResult> {
        val root = Json.parseToJsonElement(json).jsonObject
        if (root.containsKey("remark")) throw IOException("Nearby search did not complete")
        val elements = root["elements"] as? JsonArray ?: throw IOException("Nearby search returned no data")
        fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
        val seenIds = mutableSetOf<String>()
        val candidates = elements.mapNotNull { raw ->
            val item = raw as? JsonObject ?: return@mapNotNull null
            val tags = item["tags"] as? JsonObject ?: return@mapNotNull null
            if (tags.text("access") in setOf("private", "no") || tags.text("shop") in setOf("vacant", "closed", "disused", "no")) return@mapNotNull null
            val coordinates = if (item.containsKey("lat")) item else item["center"] as? JsonObject ?: return@mapNotNull null
            val latitude = (coordinates["lat"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val longitude = (coordinates["lon"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            if (!latitude.isFinite() || latitude !in -90.0..90.0 || !longitude.isFinite() || longitude !in -180.0..180.0) return@mapNotNull null
            val point = GeoPoint(latitude, longitude)
            val distance = origin.distanceTo(point)
            if (distance > radius) return@mapNotNull null
            val id = item.text("type") + ":" + item.text("id")
            if (item.text("id").isNotBlank() && !seenIds.add(id)) return@mapNotNull null
            val street = listOf(tags.text("addr:housenumber"), tags.text("addr:street")).filter(String::isNotBlank).joinToString(" ")
            val address = tags.text("addr:full").ifBlank {
                listOf(street, tags.text("addr:suburb"), tags.text("addr:city")).filter(String::isNotBlank).distinct().joinToString(", ")
            }
            val name = tags.text("name:en").ifBlank { tags.text("name") }.ifBlank { tags.text("brand") }
                .ifBlank { tags.text("operator") }.ifBlank { category.label }
            PlaceResult(name, address, point, distance.toInt())
        }.sortedBy(PlaceResult::distanceMetres)
        // A labelled node and a building polygon can describe the same business.
        return candidates.fold(mutableListOf()) { unique, place ->
            if (unique.none { existing -> place.name != category.label && existing.name.equals(place.name, true) &&
                    existing.point.distanceTo(place.point) < 25 &&
                    (existing.address == place.address || existing.address.isBlank() || place.address.isBlank()) }) unique.add(place)
            unique
        }
    }
}
