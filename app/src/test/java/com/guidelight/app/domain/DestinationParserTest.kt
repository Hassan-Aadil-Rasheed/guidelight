package com.guidelight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationParserTest {
    @Test fun parsesNavigationCommand() {
        assertEquals("Central Park", DestinationParser.parse("Navigate to Central Park"))
    }

    @Test fun parsesFromToPhrase() {
        assertEquals("India Gate", DestinationParser.parse("I want to go from here to India Gate"))
    }

    @Test fun acceptsPlainPlaceName() {
        assertEquals("Connaught Place", DestinationParser.parse("Connaught Place"))
    }

    @Test fun rejectsBlankInput() {
        assertNull(DestinationParser.parse("   "))
    }
}

