package com.guidelight.app.navigation

import com.guidelight.app.domain.DestinationParser
import org.junit.Assert.*
import org.junit.Test

class NearbyCategoryTest {
    @Test fun recognisesChemistAndLocalSynonyms() {
        listOf("Nearest chemist", "pharmacy near me", "Please find me the closest medical store.", "chhemist",
            "medical shop near my location").forEach { assertEquals(it, NearbyCategory.CHEMIST, NearbyCategory.fromQuery(it)) }
    }
    @Test fun recognisesShopsAndOtherNearbyCategories() {
        assertEquals(NearbyCategory.SHOPS, NearbyCategory.fromQuery("shops around me"))
        assertEquals(NearbyCategory.GROCERIES, NearbyCategory.fromQuery("nearest kirana shop"))
        assertEquals(NearbyCategory.RESTAURANT, NearbyCategory.fromQuery("show me restaurants nearby"))
        assertEquals(NearbyCategory.ATM, NearbyCategory.fromQuery("a cash machine close to me"))
        assertEquals(NearbyCategory.HOSPITAL, NearbyCategory.fromQuery("nearest hospital"))
    }
    @Test fun spokenNavigationCommandsKeepTheirCategory() {
        val destination = DestinationParser.parse("Take me to the nearest chemist!")!!
        assertEquals(NearbyCategory.CHEMIST, NearbyCategory.fromQuery(destination))
    }
    @Test fun preservesNamedPlacesAndExplicitLocations() {
        listOf("Apollo Pharmacy", "Nehru Place", "chemist in Mumbai", "India Gate", "coffee house road", "").forEach {
            assertNull(it, NearbyCategory.fromQuery(it))
        }
    }
    @Test fun clinicCategoriesDoNotReplaceAnExactClinicName() {
        assertEquals(NearbyCategory.CLINIC, NearbyCategory.fromQuery("homeopathic clinics near me"))
        assertEquals(NearbyCategory.CLINIC, NearbyCategory.fromQuery("nearest doctor"))
        assertNull(NearbyCategory.fromQuery("Dr Azad Homoeopathic Clinic"))
    }
}
