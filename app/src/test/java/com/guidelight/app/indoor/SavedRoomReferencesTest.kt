package com.guidelight.app.indoor

import org.junit.Assert.*
import org.junit.Test

class SavedRoomReferencesTest {
    private fun node(id: Int, stamp: Double=10.0, weight: Int=0, x: Float=0f, map: Int=0) =
        SavedViewNode(id,stamp,map,weight,x,0f,0f)
    @Test fun discardedRoomReferenceUsesAnAdjacentRetainedView() {
        val anchor=node(61,weight=-9)
        assertEquals(65,SavedRoomReferences.resolve(anchor,listOf(anchor,node(65,14.32,x=.197f))))
    }
    @Test fun distantOlderDifferentMapAndNonfiniteViewsAreRejected() {
        val anchor=node(1,weight=-9)
        listOf(node(2,x=.3f),node(2,16.0),node(2,map=1),node(2,x=Float.NaN),node(2,weight=-9))
            .forEach { assertNull(SavedRoomReferences.resolve(anchor,listOf(it))) }
    }
    @Test fun retainedAnchorDoesNotChangeToAnotherRoomsView() {
        val anchor=node(1)
        assertEquals(1,SavedRoomReferences.resolve(anchor,listOf(anchor,node(2))))
    }
    @Test fun conflictingRoomNamesNeverBecomeOneReportedLocation() {
        val nodes=listOf(node(1,weight=-9),node(2,10.5))
        assertTrue(SavedRoomReferences.rebind(listOf(SavedRoomView(1,"kitchen"),SavedRoomView(2,"hall")),nodes).isEmpty())
    }
    @Test fun missingReferencesStayUnknownRatherThanPickingAnotherRoom() {
        assertTrue(SavedRoomReferences.rebind(listOf(SavedRoomView(99,"kitchen")),listOf(node(2))).isEmpty())
    }
}
