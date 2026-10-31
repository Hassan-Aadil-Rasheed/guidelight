package com.guidelight.app.navigation

import org.junit.Assert.*
import org.junit.Test

class NearbyPlaceResultsTest {
    private val origin = GeoPoint(0.0, 0.0)
    private fun parse(elements: String) = NearbyPlaceResults.parse("""{"elements":[$elements]}""", NearbyCategory.CHEMIST, origin, 1500)

    @Test fun sortsNodesAndBuildingCentresByDistance() {
        val places = parse("""
            {"type":"way","id":2,"center":{"lat":0.002,"lon":0},"tags":{"name":"Further Chemist"}},
            {"type":"node","id":1,"lat":0.001,"lon":0,"tags":{"name":"Near Chemist","addr:housenumber":"12","addr:street":"Test Road"}}
        """)
        assertEquals(listOf("Near Chemist", "Further Chemist"), places.map { it.name })
        assertEquals("12 Test Road", places.first().address)
        assertEquals(111, places.first().distanceMetres)
    }
    @Test fun skipsInvalidPrivateAndDistantResults() {
        assertTrue(parse("""
            {"lat":91,"lon":0,"tags":{"name":"Invalid"}},
            {"lat":0.1,"lon":0,"tags":{"name":"Distant"}},
            {"lat":0.001,"lon":0,"tags":{"access":"private"}},
            {"lat":0.001,"lon":0,"tags":{"shop":"vacant"}},
            {"tags":{"name":"No coordinates"}}
        """).isEmpty())
    }
    @Test fun duplicateMappingDoesNotHideADifferentBranch() {
        val places = parse("""
            {"type":"node","id":1,"lat":0.001,"lon":0,"tags":{"name":"Same Brand"}},
            {"type":"way","id":2,"center":{"lat":0.00101,"lon":0},"tags":{"name":"Same Brand"}},
            {"type":"node","id":3,"lat":0.005,"lon":0,"tags":{"name":"Same Brand"}}
        """)
        assertEquals(2, places.size)
    }
    @Test fun unnamedShopsKeepTheirCategoryAndCoordinates() {
        val place = parse("""{"lat":0.001,"lon":0,"tags":{"amenity":"pharmacy"}}""").single()
        assertEquals("Chemist", place.name)
        assertEquals(GeoPoint(0.001, 0.0), place.point)
    }
    @Test fun timeoutOrMalformedEnvelopeIsNotReportedAsNoNearbyPlaces() {
        assertTrue(runCatching { NearbyPlaceResults.parse("""{"remark":"runtime error","elements":[]}""", NearbyCategory.CHEMIST, origin, 1500) }.isFailure)
        assertTrue(runCatching { NearbyPlaceResults.parse("{}", NearbyCategory.CHEMIST, origin, 1500) }.isFailure)
    }
}
