package com.guidelight.app.navigation

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class DailyNearbyTest {
    @Test fun religionSpecificVoiceRequestsUseTheRightCategory() {
        mapOf("nearest temple" to NearbyCategory.TEMPLE, "nearest mandir" to NearbyCategory.MANDIR,
            "closest masjid" to NearbyCategory.MOSQUE, "churches near me" to NearbyCategory.CHURCH,
            "nearest gurudwara" to NearbyCategory.GURDWARA, "nearest synagogue" to NearbyCategory.SYNAGOGUE,
            "nearest buddhist temple" to NearbyCategory.BUDDHIST_TEMPLE).forEach { (query, expected) ->
                assertEquals(query, expected, NearbyRequest.fromQuery(query)?.category)
            }
    }
    @Test fun dailyNeedsAreRecognisedAndSpecificTypesStaySpecific() {
        mapOf("nearest toilet" to NearbyCategory.TOILET, "nearest drinking water" to NearbyCategory.WATER,
            "nearest metro station" to NearbyCategory.METRO, "nearest bus stop" to NearbyCategory.BUS_STOP,
            "nearest bus station" to NearbyCategory.BUS_STATION, "nearest railway station" to NearbyCategory.TRAIN_STATION,
            "nearest bank" to NearbyCategory.BANK, "nearest dentist" to NearbyCategory.DENTIST,
            "nearest optician" to NearbyCategory.OPTICIAN, "nearest bakery" to NearbyCategory.BAKERY,
            "nearest library" to NearbyCategory.LIBRARY, "nearest police station" to NearbyCategory.POLICE,
            "nearest supermarket" to NearbyCategory.SUPERMARKET, "nearest cafe" to NearbyCategory.CAFE,
            "nearest restaurant" to NearbyCategory.RESTAURANT, "nearest vegetable shop" to NearbyCategory.FRUIT_VEG).forEach { (query, category) ->
                assertEquals(query, category, NearbyRequest.fromQuery(query)?.category)
            }
    }
    @Test fun catalogQueriesRemainBoundedAndReligionFiltersDoNotBecomeGenericWorshipSearches() {
        val service = NearbyPlaceSearchService()
        NearbyCategory.entries.forEach { category ->
            val query = service.query(category, GeoPoint(0.0, 0.0), 250)
            assertTrue(category.name, query.contains("around:250,0.000000,0.000000"))
            assertTrue(category.name, query.contains("[access!~"))
            assertTrue(category.name, category.searchWords.isNotEmpty())
        }
        assertTrue(service.query(NearbyCategory.MOSQUE, GeoPoint(0.0, 0.0), 500).contains("[religion=muslim]"))
        assertTrue(service.query(NearbyCategory.CHURCH, GeoPoint(0.0, 0.0), 500).contains("[religion=christian]"))
        assertTrue(service.query(NearbyCategory.MANDIR, GeoPoint(0.0, 0.0), 500).contains("[religion=hindu]"))
    }
    @Test fun unlistedNearestRequestsAreLocalWhileNamedAndCitySpecificPlacesStayIntact() {
        assertEquals("florist", NearbyRequest.fromQuery("Could you find the nearest florist near me?")?.term)
        assertNull(NearbyRequest.fromQuery("nearest florist")?.category)
        listOf("Apollo Pharmacy", "florist", "Dr Azad Clinic", "nearest chemist in Mumbai", "nearest shop];out;", "nearest").forEach {
            assertNull(it, NearbyRequest.fromQuery(it))
        }
    }
    @Test fun genericNearestSearchUsesBoundedPoiGeocoderInsteadOfWorldwideNameSearch() = runBlocking {
        MockWebServer().use { overpass -> MockWebServer().use { geocoder ->
            geocoder.enqueue(MockResponse().setBody("""[{"lat":"0.001","lon":"0","name":"Example florist","display_name":"Example florist, Test Road"}]"""))
            val service = PlaceSearchService(geocoder.url("").toString().trimEnd('/'), NearbyPlaceSearchService(overpass.url("/").toString()))
            assertEquals("Example florist", service.search("nearest florist", GeoPoint(0.0, 0.0)).single().name)
            val query = geocoder.takeRequest().requestUrl!!
            assertEquals("1", query.queryParameter("bounded"))
            assertEquals("poi", query.queryParameter("layer"))
            assertEquals("florist", query.queryParameter("q"))
            assertNotNull(query.queryParameter("viewbox"))
            assertEquals(0, overpass.requestCount)
        } }
    }
    @Test fun worshipFallbackDoesNotPresentAnotherReligionAsTheRequestedOne() {
        val json = """[{"name":"Example church","extratags":{"religion":"christian"}},
            {"name":"Example mosque","extratags":{"religion":"muslim"}},
            {"name":"Unknown worship place"}]"""
        val filtered = PlaceSearchService().filterReligion(json, NearbyCategory.MOSQUE.religions)
        assertTrue(filtered.contains("Example mosque"))
        assertFalse(filtered.contains("Example church"))
        assertFalse(filtered.contains("Unknown worship place"))
    }
}
