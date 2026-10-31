package com.guidelight.app.navigation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

/** Bounded category searches, made only when the user requests nearby places. */
class NearbyPlaceSearchService(private val endpoint: String = "https://overpass-api.de/api/interpreter") {
    private data class Cached(val at: Long, val places: List<PlaceResult>)
    private val cache = linkedMapOf<String, Cached>()
    private val mutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun search(category: NearbyCategory, origin: GeoPoint): List<PlaceResult> = mutex.withLock {
        val key = String.format(Locale.US, "%s:%.5f:%.5f", category.name, origin.latitude, origin.longitude)
        val now = System.nanoTime() / 1_000_000
        cache[key]?.takeIf { now - it.at < 300_000 }?.let { cached ->
            return@withLock cached.places.map { it.copy(distanceMetres = origin.distanceTo(it.point).toInt()) }
                .sortedBy(PlaceResult::distanceMetres).take(5)
        }
        suspend fun fetch(radius: Int): List<PlaceResult> {
            val wait = 1_050 - (System.nanoTime() / 1_000_000 - lastRequestAt)
            if (wait > 0) delay(wait)
            lastRequestAt = System.nanoTime() / 1_000_000
            val body = GuideHttp.postForm(endpoint, "data", query(category, origin, radius))
            return withContext(Dispatchers.Default) { NearbyPlaceResults.parse(body, category, origin, radius) }
        }
        var places = emptyList<PlaceResult>()
        for (radius in NearbySearchPolicy.radiiMetres) {
            places = fetch(radius)
            if (places.isNotEmpty()) break
        }
        if (cache.size >= 20) cache.remove(cache.keys.first())
        cache[key] = Cached(System.nanoTime() / 1_000_000, places)
        places.take(5)
    }

    internal fun query(category: NearbyCategory, origin: GeoPoint, radius: Int): String {
        require(origin.latitude.isFinite() && origin.latitude in -90.0..90.0 &&
            origin.longitude.isFinite() && origin.longitude in -180.0..180.0)
        require(radius in 1..5_000)
        val filters = category.filters
        val around = String.format(Locale.US, "(around:%d,%.6f,%.6f)", radius, origin.latitude, origin.longitude)
        return "[out:json][timeout:12][maxsize:16777216];(" + filters.joinToString("") {
            "nwr$it[access!~\"^(private|no)$\"]$around;"
        } + ");out body center;"
    }
}
