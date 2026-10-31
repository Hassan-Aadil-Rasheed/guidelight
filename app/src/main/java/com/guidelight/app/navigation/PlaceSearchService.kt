package com.guidelight.app.navigation

import kotlinx.serialization.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.cos

class PlaceSearchService(
    private val baseUrl: String = "https://nominatim.openstreetmap.org",
    private val nearby: NearbyPlaceSearchService = NearbyPlaceSearchService()
) {
    private var lastRequestAt = 0L
    private val cache = mutableMapOf<String, List<PlaceResult>>()
    private val mutex = Mutex()
    private data class NearbyCache(val at: Long, val places: List<PlaceResult>)
    private val nearbyFallbackCache = linkedMapOf<String, NearbyCache>()
    private var nearbyUnavailableUntil = 0L

    suspend fun search(query: String, origin: GeoPoint): List<PlaceResult> {
        NearbyRequest.fromQuery(query)?.let { request ->
            val category = request.category
            if (category != null && System.nanoTime() / 1_000_000 >= nearbyUnavailableUntil) {
                try { nearby.search(category, origin).takeIf { it.isNotEmpty() }?.let { return it } }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { nearbyUnavailableUntil = System.nanoTime() / 1_000_000 + 300_000 }
            }
            return searchNearbyFallback(request, origin)
        }
        return searchNamedPlace(query, origin)
    }

    private suspend fun searchNamedPlace(query: String, origin: GeoPoint): List<PlaceResult> = mutex.withLock {
        val cacheKey = "${query.lowercase(Locale.US)}:${"%.2f".format(Locale.US, origin.latitude)}:${"%.2f".format(Locale.US, origin.longitude)}"
        cache[cacheKey]?.let { return@withLock it }
        waitForGeocoder()
        val delta = 0.50
        val viewbox = listOf(
            origin.longitude - delta,
            origin.latitude + delta,
            origin.longitude + delta,
            origin.latitude - delta
        ).joinToString(",") { String.format(Locale.US, "%.6f", it) }
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        // viewbox ranks nearby matches first. It deliberately remains unbounded so an
        // address outside the local area can still be found.
        val json = GuideHttp.get("$baseUrl/search?format=jsonv2&addressdetails=1&limit=8&q=$encodedQuery&viewbox=$viewbox")
        parseResults(json, origin).also {
            if (cache.size >= 50) cache.clear()
            cache[cacheKey] = it
        }
    }

    private suspend fun waitForGeocoder() {
        val wait = 1_050L - (System.nanoTime() / 1_000_000 - lastRequestAt)
        if (wait > 0) delay(wait)
        lastRequestAt = System.nanoTime() / 1_000_000
    }

    /** Nominatim supports small bounded amenity searches; this returns a shortlist, not a POI download. */
    private suspend fun searchNearbyFallback(request: NearbyRequest, origin: GeoPoint): List<PlaceResult> = mutex.withLock {
        val category = request.category
        val key = String.format(Locale.US, "%s:%.5f:%.5f", category?.name ?: "generic:${request.term}", origin.latitude, origin.longitude)
        val now = System.nanoTime() / 1_000_000
        nearbyFallbackCache[key]?.takeIf { now - it.at < 300_000 }?.let { return@withLock it.places }
        val keywords = category?.searchWords ?: listOf(request.term)
        for (radius in NearbySearchPolicy.radiiMetres) {
            val latitudeDelta = radius.toDouble() / 111_195
            val longitudeDelta = latitudeDelta / cos(Math.toRadians(origin.latitude)).coerceAtLeast(.01)
            val viewbox = listOf((origin.longitude - longitudeDelta).coerceAtLeast(-180.0),
                (origin.latitude + latitudeDelta).coerceAtMost(90.0), (origin.longitude + longitudeDelta).coerceAtMost(180.0),
                (origin.latitude - latitudeDelta).coerceAtLeast(-90.0)).joinToString(",") { String.format(Locale.US, "%.6f", it) }
            val matches = mutableListOf<PlaceResult>()
            var failure: Exception? = null
            for (keyword in keywords) {
                waitForGeocoder()
                try {
                    val encoded = URLEncoder.encode(if (category == null) keyword else "[$keyword]", Charsets.UTF_8.name())
                    val json = GuideHttp.get("$baseUrl/search?format=jsonv2&addressdetails=1&extratags=1&limit=${NearbySearchPolicy.MAX_CANDIDATES}&layer=poi&bounded=1&q=$encoded&viewbox=$viewbox")
                    matches += parseResults(filterReligion(json, category?.religions), origin, limit = NearbySearchPolicy.MAX_CANDIDATES)
                        .filter { origin.distanceTo(it.point) <= radius }
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { failure = error }
            }
            // A failed close-area lookup must not be treated as evidence that only distant shops exist.
            if (matches.isEmpty()) failure?.let { throw it }
            if (matches.isNotEmpty()) {
                val places = matches.distinctBy { it.point }.sortedBy(PlaceResult::distanceMetres).take(NearbySearchPolicy.MAX_SUGGESTIONS)
                if (nearbyFallbackCache.size >= 20) nearbyFallbackCache.remove(nearbyFallbackCache.keys.first())
                nearbyFallbackCache[key] = NearbyCache(System.nanoTime() / 1_000_000, places)
                return@withLock places
            }
        }
        if (nearbyFallbackCache.size >= 20) nearbyFallbackCache.remove(nearbyFallbackCache.keys.first())
        nearbyFallbackCache[key] = NearbyCache(System.nanoTime() / 1_000_000, emptyList())
        emptyList()
    }

    internal fun filterReligion(json: String, religions: Set<String>?): String {
        if (religions == null) return json
        val filtered = Json.parseToJsonElement(json).jsonArray.filter { item ->
            val objectValue = item as? JsonObject
            val tags = objectValue?.get("extratags") as? JsonObject
            (tags?.get("religion") as? JsonPrimitive)?.contentOrNull in religions
        }
        return JsonArray(filtered).toString()
    }

    internal fun parseResults(json: String, origin: GeoPoint, limit: Int = 5): List<PlaceResult> {
        val array = Json.parseToJsonElement(json).jsonArray
        return buildList {
            for (raw in array) {
                val item = raw as? JsonObject ?: continue
                fun text(key: String) = (item[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val latitude = text("lat").toDoubleOrNull() ?: continue
                val longitude = text("lon").toDoubleOrNull() ?: continue
                if (!latitude.isFinite() || latitude !in -90.0..90.0 || !longitude.isFinite() || longitude !in -180.0..180.0) continue
                val point = GeoPoint(latitude, longitude)
                val display = text("display_name").ifBlank { "Unknown place" }
                val parts = display.split(',').map(String::trim).filter(String::isNotBlank)
                add(
                    PlaceResult(
                        name = text("name").ifBlank { parts.firstOrNull() ?: "Unknown place" },
                        address = parts.drop(1).take(3).joinToString(", "),
                        point = point,
                        distanceMetres = origin.distanceTo(point).toInt()
                    )
                )
            }
        }.distinctBy { "${it.name.lowercase(Locale.US)}:${it.address.lowercase(Locale.US)}:${it.point}" }
            .sortedBy(PlaceResult::distanceMetres)
            .take(limit)
    }
}
