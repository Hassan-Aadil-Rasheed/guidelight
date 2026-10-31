package com.guidelight.app.indoor

import org.junit.Assert.*
import org.junit.Test

class SavedHomeControlsTest {
    @Test fun reservedSaveCommandsTakePrecedenceOverRoomNaming() {
        listOf("Save map", "Save my home map please", "Finish mapping.")
            .forEach { assertEquals(SavedHomeControl.SAVE, SavedHomeControls.parse(it)) }
        assertNull(SavedHomeControls.parse("This is my kitchen"))
        assertNull(SavedHomeControls.parse("Save room kitchen"))
    }
    @Test fun naturalPositionQuestionsAreRecognized() {
        listOf("Where am I?", "Please tell me my location.", "Which room am I in?", "Where am I now?")
            .forEach { assertEquals(SavedHomeControl.LOCATE, SavedHomeControls.parse(it)) }
    }
    @Test fun uncertainAndUntrackedPositionsNeverClaimARoom() {
        assertFalse(SavedHomeControls.locationReply(false,true,"kitchen","More light needed.").contains("matches the saved"))
        assertFalse(SavedHomeControls.locationReply(true,false,"kitchen","Ready").contains("matches the saved"))
        assertTrue(SavedHomeControls.locationReply(true,true,null,"Ready").contains("cannot identify this room"))
        assertEquals("Your location matches the saved kitchen view.",SavedHomeControls.locationReply(true,true,"kitchen","Ready"))
    }
}
