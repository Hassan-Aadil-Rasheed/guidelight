package com.guidelight.app.vision

import org.junit.Assert.*
import org.junit.Test

class LocalAiPolicyTest {
    @Test fun spokenWisprRequestsAndCancelAreRecognized() {
        assertEquals(LocalAiTask.DESCRIBE,LocalAiCommands.parse("Please describe the view."))
        assertEquals(LocalAiTask.IDENTIFY_ROOM,LocalAiCommands.parse("Identify this room"))
        assertEquals(LocalAiTask.EXPLAIN_STATUS,LocalAiCommands.parse("Explain my status"))
        assertEquals(LocalAiTask.DETAILED_VIEW,LocalAiCommands.parse("Describe the view in detail"))
        assertTrue(LocalAiCommands.isCancel("Stop thinking."))
        assertNull(LocalAiCommands.parse("End walk"))
    }
    @Test fun generatedMovementCommandsCannotBeSpokenAsObjectLabels() {
        val unsafe="""{"scene_type":"kitchen","objects":[{"name":"turn left and walk","side":"ahead"}]}"""
        assertTrue(runCatching {LocalAiPolicy.parseScene(unsafe)}.isFailure)
        assertTrue(runCatching {LocalAiPolicy.parseScene("not JSON")}.isFailure)
    }
    @Test fun visualRoomResemblanceDoesNotBecomeVerifiedPosition() {
        val scene=AiScene("kitchen",listOf(AiObject("chair","left")),"R1")
        val spoken=LocalAiPolicy.describe(scene,listOf(AiRoomReference("R1","kitchen")),null)
        assertTrue(spoken.contains("Your position is not verified"))
        assertFalse(spoken.contains("Your position also matches"))
        assertTrue(LocalAiPolicy.describe(scene,listOf(AiRoomReference("R1","kitchen")),"kitchen").contains("Your position also matches"))
        assertTrue(runCatching {LocalAiPolicy.describe(scene,emptyList(),null)}.isFailure)
    }
    @Test fun inconsistentStatusAndInventedInstructionsAreRejected() {
        assertTrue(runCatching {LocalAiPolicy.statusAnswer("""{"status":"localized","answer":"You are in the kitchen."}""","not_localized")}.isFailure)
        assertTrue(runCatching {LocalAiPolicy.statusAnswer("""{"status":"not_localized","answer":"Turn left."}""","not_localized")}.isFailure)
        val checked=LocalAiPolicy.statusAnswer("""{"status":"not_localized","answer":"You are in the kitchen."}""","not_localized")
        assertFalse(checked.contains("kitchen"))
        assertTrue(checked.contains("not verified"))
    }
    @Test fun poorCameraFramesAreNotSentForSpeculativeRecognition() {
        assertFalse(AiImageQuality.usableLuma(ByteArray(1024),1024))
        assertFalse(AiImageQuality.usableLuma(ByteArray(1024){255.toByte()},1024))
        assertFalse(AiImageQuality.usableLuma(ByteArray(1024){100},1024))
        assertTrue(AiImageQuality.usableLuma(ByteArray(1024){if(it%32==0)60 else 160.toByte()},1024))
    }
    @Test fun absentNullAndEmptyReasoningAreNeverReportedAsRealThinking() {
        listOf("null","\"\"").forEach { thinking ->
            assertTrue(runCatching {LocalAiReply.parse("""{"choices":[{"message":{"content":"{}","reasoning_content":$thinking}}]}""")}.isFailure)
        }
        assertTrue(runCatching {LocalAiReply.parse("""{"choices":[{"message":{"content":"{}"}}]}""")}.isFailure)
        assertTrue(LocalAiReply.parse("""{"choices":[{"message":{"content":"{}","reasoning_content":"A generated analysis"}}]}""").usedReasoning)
    }
    @Test fun fastVisionDoesNotClaimToHaveGeneratedReasoning() {
        val reply="""{"choices":[{"message":{"content":"{}","reasoning_content":null}}]}"""
        assertFalse(LocalAiReply.parse(reply,requireReasoning=false).usedReasoning)
        assertTrue(runCatching {LocalAiReply.parse(reply,requireReasoning=true)}.isFailure)
        assertTrue(runCatching {LocalAiReply.parse("""{"choices":[{"message":{"content":""}}]}""",false)}.isFailure)
    }
    @Test fun contradictoryVisualAndTrackedRoomsDoNotProduceAPositionClaim() {
        val answer=LocalAiPolicy.describe(AiScene("kitchen",reference_match="R1"),listOf(AiRoomReference("R1","kitchen")),"hall")
        assertTrue(answer.contains("cannot confirm a room"))
        assertFalse(answer.contains("Your position also matches"))
    }
    @Test fun parseFailuresDoNotReadUntrustedGeneratedInstructionsAloud() {
        val message=LocalAiPolicy.failureMessage(kotlinx.serialization.SerializationException("Invalid JSON: turn left and walk ten meters"))
        assertFalse(message.contains("turn left"))
        assertTrue(message.contains("could not be verified"))
    }
}
