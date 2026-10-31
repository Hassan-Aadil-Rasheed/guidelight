package com.guidelight.app.navigation

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class NearbyPlaceSearchServiceTest {
    private val origin = GeoPoint(0.0, 0.0)
    private fun response(vararg latitudes: Double) = """{"elements":[""" + latitudes.mapIndexed { i, latitude ->
        """{"type":"node","id":$i,"lat":$latitude,"lon":0,"tags":{"amenity":"pharmacy","name":"Chemist $i"}}"""
    }.joinToString(",") + "]}"

    @Test fun nearestChemistUsesCategoryPostAndCachesSynonyms() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response(.002, .0018, .0015, .0012, .001)))
            val nearby = NearbyPlaceSearchService(server.url("/api/interpreter").toString())
            val search = PlaceSearchService(server.url("/name-search").toString(), nearby)
            val places = search.search("Nearest chemist", origin)
            assertEquals(5, places.size)
            assertEquals("Chemist 4", places.first().name)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("POST", request.method)
            assertEquals("/api/interpreter", request.path)
            val query = URLDecoder.decode(request.body.readUtf8().removePrefix("data="), "UTF-8")
            assertTrue(query.contains("around:250,0.000000,0.000000"))
            assertTrue(query.contains("amenity=pharmacy"))
            // 'out tags' omits node coordinates even when 'center' is requested.
            assertTrue(query.endsWith("out body center;"))
            assertFalse(query.contains("Nearest chemist"))
            assertEquals(places, search.search("medical stores near me", origin))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun sparseAreaChecksWalkingDistancesBeforeFiveKilometres() = runBlocking {
        MockWebServer().use { server ->
            repeat(4) { server.enqueue(MockResponse().setBody(response())) }
            server.enqueue(MockResponse().setBody(response(.02, .06)))
            val places = NearbyPlaceSearchService(server.url("/").toString()).search(NearbyCategory.CHEMIST, origin)
            assertEquals(1, places.size)
            assertTrue(places.single().distanceMetres in 2000..2500)
            val queries = (1..5).map { URLDecoder.decode(server.takeRequest().body.readUtf8(), "UTF-8") }
            listOf(250,500,1000,2000,5000).zip(queries).forEach { (radius, query) -> assertTrue(query.contains("around:$radius,")) }
            assertEquals(5, server.requestCount)
        }
    }

    @Test fun incompleteProviderResponseIsAnErrorAndCanBeRetried() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"remark":"runtime timeout","elements":[]}"""))
            server.enqueue(MockResponse().setBody(response(.001, .0012, .0015, .0018, .002)))
            val service = NearbyPlaceSearchService(server.url("/").toString())
            assertTrue(runCatching { service.search(NearbyCategory.CHEMIST, origin) }.isFailure)
            assertEquals(5, service.search(NearbyCategory.CHEMIST, origin).size)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun oneClosePlaceStopsUnnecessaryWiderSearches() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response(.001)))
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(1, NearbyPlaceSearchService(server.url("/").toString()).search(NearbyCategory.CHEMIST, origin).size)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun queryRejectsInvalidCoordinatesAndUnboundedRadius() {
        val service = NearbyPlaceSearchService()
        assertTrue(runCatching { service.query(NearbyCategory.SHOPS, GeoPoint(91.0, 0.0), 1500) }.isFailure)
        assertTrue(runCatching { service.query(NearbyCategory.SHOPS, origin, 50_000) }.isFailure)
    }
}
