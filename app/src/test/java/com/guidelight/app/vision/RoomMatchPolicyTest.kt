package com.guidelight.app.vision

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*

class RoomMatchPolicyTest {
    private val refs=listOf(AiRoomReference("R1","kitchen"),AiRoomReference("R2","hall"))
    private val evidence="The doorway and fixed cabinet handle layout match the saved view."
    @Test fun wrongIdsAndCategoryOnlyGuessesAreRejected() {
        assertTrue(runCatching {RoomMatchPolicy.parse("""{"reference_match":"R9","ambiguous":false,"evidence":"$evidence"}""",refs)}.isFailure)
        assertNull(RoomMatchPolicy.parse("""{"reference_match":"R1","ambiguous":false,"evidence":"Both images look like a kitchen."}""",refs).reference_match)
        assertTrue(runCatching {RoomMatchPolicy.parse("""{"scene_type":"kitchen","objects":[]}""",refs)}.isFailure)
    }
    @Test fun aCaptionCannotTurnARoomCategoryIntoASavedRoom() {
        val comparison=RoomComparison("R1",false,evidence)
        assertNull(RoomMatchPolicy.grounded(comparison,"The current image looks like a kitchen.").reference_match)
        assertNull(RoomMatchPolicy.grounded(comparison,"R1 does not match the current view.").reference_match)
        assertEquals("R1",RoomMatchPolicy.grounded(comparison,"CURRENT matches R1: the doorway and cabinet layout are the same.").reference_match)
    }
    @Test fun contradictoryEvidenceCannotConfirmARoom() {
        val raw="""{"reference_match":"R1","ambiguous":false,"evidence":"The doorway and cabinet layout do not match the current view."}"""
        assertNull(RoomMatchPolicy.parse(raw,refs).reference_match)
        assertNull(RoomMatchPolicy.grounded(RoomComparison("R1",false,evidence),"R1 doesn't match the doorway in CURRENT.").reference_match)
    }
    @Test fun competingRoomsAndIncompleteSearchDoNotBecomeLocationClaims() {
        val ambiguous=RoomMatchPolicy.combine(listOf(RoomComparison("R1",evidence=evidence),RoomComparison("R2",evidence=evidence)),refs)
        assertTrue(ambiguous.ambiguous);assertNull(ambiguous.reference_match)
        val partial=RoomMatchResult(RoomComparison("R1",evidence=evidence),false,100)
        assertFalse(RoomMatchPolicy.describe(partial,refs,null).contains("saved kitchen"))
        val full=partial.copy(complete=true)
        assertTrue(RoomMatchPolicy.describe(full,refs,null).contains("not confirmed"))
        assertTrue(RoomMatchPolicy.describe(full,refs,"hall").contains("disagrees"))
    }
    @Test fun allComparisonRequestsCompleteAndConflictingMatchesAreReported()=runBlocking<Unit> {
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse {
                val id=if(request.body.readUtf8().contains("R1"))"R1" else "R2"
                return MockResponse().setBody("""{"success":true,"result":{"response":{"reference_match":"$id","ambiguous":false,"evidence":"$evidence"}}}""")
            }}
            val engine=CloudflareVisionEngine(CloudflareCredentials("0123456789abcdef0123456789abcdef","not-a-real-token-for-unit-test"),OkHttpClient(),server.url("/test"))
            try {
                val result=engine.identifyRoom(refs.map{RoomComparisonImage(byteArrayOf(1),listOf(it))})
                assertTrue(result.complete);assertTrue(result.comparison.ambiguous)
                assertEquals(2,server.requestCount)
            }finally{engine.close()}
        }
    }
}
