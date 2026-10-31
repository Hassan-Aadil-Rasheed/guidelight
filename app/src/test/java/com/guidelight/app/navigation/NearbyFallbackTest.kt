package com.guidelight.app.navigation

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder

class NearbyFallbackTest {
    private val origin = GeoPoint(0.0, 0.0)
    private fun places() = """[
        {"lat":"0.002","lon":"0","name":"Further Chemist","display_name":"Further Chemist, Test Road"},
        {"lat":"0.001","lon":"0","name":"Near Chemist","display_name":"Near Chemist, Test Road"},
        {"lat":"0.1","lon":"0","name":"Outside radius","display_name":"Outside radius, Far Road"}
    ]"""

    @Test fun providerFailureUsesBoundedFallbackCachesAndTemporarilySkipsFailedProvider() = runBlocking {
        MockWebServer().use { overpass -> MockWebServer().use { geocoder ->
            overpass.enqueue(MockResponse().setResponseCode(503))
            geocoder.enqueue(MockResponse().setBody(places()))
            geocoder.enqueue(MockResponse().setBody(places()))
            val service = PlaceSearchService(geocoder.url("").toString().trimEnd('/'), NearbyPlaceSearchService(overpass.url("/").toString()))
            val found = service.search("nearest chemist", origin)
            assertEquals(listOf("Near Chemist", "Further Chemist"), found.map { it.name })
            val query = URLDecoder.decode(geocoder.takeRequest().path!!, "UTF-8")
            assertTrue(query.contains("q=[pharmacy]"))
            assertTrue(query.contains("bounded=1"))
            assertTrue(query.contains("layer=poi"))
            assertEquals(found, service.search("pharmacy near me", origin))
            assertEquals(1, geocoder.requestCount)
            assertEquals(2, service.search("nearest ATM", origin).size)
            assertEquals(1, overpass.requestCount)
        } }
    }

    @Test fun fallbackForShopsCombinesEverydayShopTypesWithoutUnboundedSearch() = runBlocking {
        MockWebServer().use { overpass -> MockWebServer().use { geocoder ->
            overpass.enqueue(MockResponse().setResponseCode(503))
            geocoder.enqueue(MockResponse().setBody(places()))
            geocoder.enqueue(MockResponse().setBody(places()))
            val service = PlaceSearchService(geocoder.url("").toString().trimEnd('/'), NearbyPlaceSearchService(overpass.url("/").toString()))
            assertEquals(2, service.search("shops near my location", origin).size)
            val requests = (1..2).map { URLDecoder.decode(geocoder.takeRequest().path!!, "UTF-8") }
            assertTrue(requests[0].contains("q=[convenience store]"))
            assertTrue(requests[1].contains("q=[supermarket]"))
            assertTrue(requests.all { it.contains("bounded=1") && it.contains("limit=40") })
        } }
    }

    @Test fun existingNamedPlaceParsingRetainsValidCoordinatesAndSkipsInvalidRecords() {
        val json = """[{"lat":"NaN","lon":"0"},{"lat":"0.001","lon":"0","display_name":"Test Place, Test Road"}]"""
        assertEquals("Test Place", PlaceSearchService().parseResults(json, origin).single().name)
    }
    @Test fun aClinicAt350MetresStopsExpansionBeforeFartherResults() = runBlocking {
        MockWebServer().use { overpass -> MockWebServer().use { geocoder ->
            overpass.enqueue(MockResponse().setResponseCode(503))
            geocoder.enqueue(MockResponse().setBody("[]"))
            geocoder.enqueue(MockResponse().setBody("[]"))
            val clinic = """[{"lat":"0.00315","lon":"0","name":"Example Homeopathic Clinic","display_name":"Example Homeopathic Clinic, Test Road"}]"""
            geocoder.enqueue(MockResponse().setBody(clinic))
            geocoder.enqueue(MockResponse().setBody("[]"))
            val service = PlaceSearchService(geocoder.url("").toString().trimEnd('/'), NearbyPlaceSearchService(overpass.url("/").toString()))
            val found = service.search("nearest homeopathic clinic", origin)
            assertEquals("Example Homeopathic Clinic", found.single().name)
            assertTrue(found.single().distanceMetres in 345..355)
            assertEquals(4, geocoder.requestCount)
            val requests = (1..4).map { geocoder.takeRequest().requestUrl!! }
            val firstBox = requests[0].queryParameter("viewbox")!!.split(',').map(String::toDouble)
            val secondBox = requests[2].queryParameter("viewbox")!!.split(',').map(String::toDouble)
            assertTrue(firstBox[1] in .002.. .0025)
            assertTrue(secondBox[1] in .004.. .005)
        } }
    }
    @Test fun differentBranchesWithSameNameAndAddressKeepTheCloserOne() {
        val json = """[
            {"lat":"0.02","lon":"0","name":"Same Chemist","display_name":"Same Chemist, Test Road"},
            {"lat":"0.001","lon":"0","name":"Same Chemist","display_name":"Same Chemist, Test Road"}
        ]"""
        val found = PlaceSearchService().parseResults(json, origin)
        assertEquals(2, found.size)
        assertEquals(111, found.first().distanceMetres)
    }
    @Test fun closeCandidateAfterTheFirstEightIsNotLost() = runBlocking {
        MockWebServer().use { overpass -> MockWebServer().use { geocoder ->
            overpass.enqueue(MockResponse().setResponseCode(503))
            val candidates = (0..38).map { i ->
                """{"lat":"${.001 + i * .00002}","lon":"0","name":"Shop $i","display_name":"Shop $i, Test Road"}"""
            } + """{"lat":"0.0001","lon":"0","name":"Closest","display_name":"Closest, Test Road"}"""
            geocoder.enqueue(MockResponse().setBody(candidates.joinToString(",", "[", "]")))
            val search = PlaceSearchService(geocoder.url("").toString().trimEnd('/'), NearbyPlaceSearchService(overpass.url("/").toString()))
            assertEquals("Closest", search.search("nearest chemist", origin).first().name)
            assertEquals("40", geocoder.takeRequest().requestUrl!!.queryParameter("limit"))
        } }
    }
}
