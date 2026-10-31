package com.guidelight.app.indoor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class HomeDomainTest {
    @Test fun personalMarkerIdentitySurvivesRecordingAndSerialization() {
        val recorder = HomeRecorder(HomeMap(marker = HomeMarkers.PERSONAL), HomePoint(0f, 0f, .5f))
        val saved = recorder.saveRoom("kitchen", HomePoint(0f, 0f, .5f))
        assertEquals(HomeMarkers.PERSONAL, saved.marker)
        val decoded = kotlinx.serialization.json.Json.decodeFromString<HomeMap>(
            kotlinx.serialization.json.Json.encodeToString(HomeMap.serializer(), saved)).validate()
        assertEquals(HomeMarkers.PERSONAL, decoded.marker)
        assertEquals("kitchen", decoded.rooms.single().name)
    }
    private fun p(x: Float, z: Float, y: Float = 0f) = HomePoint(x, y, z)
    private val rooms = listOf(HomeRoom("kitchen", 1), HomeRoom("washroom", 2))

    @Test fun wisprRoomLabelsAndDestinationsAreNormalized() {
        assertEquals(HomeCommand.SaveRoom("kitchen"), HomeCommands.parse("This is my kitchen.", rooms))
        assertEquals(HomeCommand.SaveRoom("dining area"), HomeCommands.parse("Save room dining area!", rooms))
        assertEquals(HomeCommand.GoTo("washroom"), HomeCommands.parse("I want to go to the washroom.", rooms))
        assertEquals(HomeCommand.GoTo("kitchen"), HomeCommands.parse("Please take me to my kitchen", rooms))
    }
    @Test fun numberedRoomsAndDirectRoomNamesWork() {
        assertEquals(HomeCommand.Choose(1), HomeCommands.parse("Choose suggestion two", rooms))
        assertEquals(HomeCommand.Choose(0), HomeCommands.parse("Choose room 1.", rooms))
        assertEquals(HomeCommand.GoTo("kitchen"), HomeCommands.parse("kitchen", rooms))
        assertEquals(HomeCommand.Unknown, HomeCommands.parse("Choose room 9", rooms))
    }
    @Test fun naturalRoomLabelsWorkWithoutRememberingOneExactPhrase() {
        listOf("It's my kitchen.", "It’s my kitchen.", "It is my kitchen", "Its my kitchen",
            "That's my kitchen", "That is my kitchen", "I am in my kitchen", "Remember this room as kitchen").forEach {
            assertEquals(it, HomeCommand.SaveRoom("kitchen"), HomeCommands.parse(it, rooms))
        }
    }
    @Test fun everydayListRequestsReadSavedRooms() {
        listOf("List my rooms", "List kitchen.", "List my kitchen", "List everything", "Where can I go?", "Show my rooms").forEach {
            assertEquals(it, HomeCommand.ListRooms, HomeCommands.parse(it, rooms))
        }
    }
    @Test fun namingARoomCommitsTheWalkWithoutAFinishCommand() {
        val learnedFromScan = HomeRecorder(HomeMap(), p(0f, 0f))
        learnedFromScan.observe(p(.4f, 0f))
        val label = HomeCommands.parse("It's my kitchen", emptyList()) as HomeCommand.SaveRoom
        val saved = learnedFromScan.saveRoom(label.name, p(.8f, 0f))
        assertEquals("kitchen", saved.rooms.single().name)
        assertEquals(.8f, HomeRouting.plan(saved, p(0f, 0f), saved.rooms.single()).points.zipWithNext()
            .sumOf { (a, b) -> a.distance(b).toDouble() }.toFloat(), .01f)
    }
    @Test fun canNavigateBackAlongAWalkedTailWithoutNamingAnotherRoomFirst() {
        val recorder = HomeRecorder(HomeMap(), p(0f, 0f))
        recorder.observe(p(.4f, 0f))
        val saved = recorder.saveRoom("kitchen", p(.8f, 0f))
        recorder.observe(p(.8f, .4f)); recorder.observe(p(.8f, .8f))
        val route = HomeRouting.plan(recorder.snapshot(), p(.8f, .8f), saved.rooms.single())
        assertEquals(.8f, route.points.zipWithNext().sumOf { (a, b) -> a.distance(b).toDouble() }.toFloat(), .01f)
        assertEquals(0f, saved.nodes.last().point.z, .001f)
    }
    @Test fun endWalkIsImmediateAndNegatedCommandsAreNotExecuted() {
        assertEquals(HomeCommand.EndWalk, HomeCommands.parse("End walk.", rooms))
        assertEquals(HomeCommand.Unknown, HomeCommands.parse("Don't end walk", rooms))
        assertEquals(HomeCommand.Unknown, HomeCommands.parse("this is my kitchen and stop recording", rooms))
    }
    @Test fun commandNamesAndMapLimitsAreValidated() {
        assertEquals(HomeCommand.StartRecording, HomeCommands.parse("Start recording", rooms))
        assertEquals(HomeCommand.FinishRecording, HomeCommands.parse("Finish recording", rooms))
        assertEquals(HomeCommand.ListRooms, HomeCommands.parse("List rooms", rooms))
        assertEquals(HomeCommand.Repeat, HomeCommands.parse("Repeat directions", rooms))
        assertNull(HomeCommands.roomName("../kitchen"))
        assertNull(HomeCommands.roomName("a".repeat(41)))
    }
    @Test fun savesRoomWithRecordedBreadcrumbsAndDoesNotMutateSavedSnapshot() {
        val recorder = HomeRecorder(HomeMap(), p(0f, 0f))
        recorder.observe(p(0f, .4f)); recorder.observe(p(0f, .8f))
        val saved = recorder.saveRoom("kitchen", p(.2f, .8f))
        assertEquals(1, saved.rooms.size)
        assertEquals(saved.nodes.last().id, saved.rooms.first().node)
        recorder.observe(p(.6f, .8f))
        assertEquals(.2f, saved.nodes.last().point.x, .001f)
    }
    @Test fun anotherRecordingMustBeginNearTheSavedStart() {
        val recorder = HomeRecorder(HomeMap(), p(0f, 0f))
        val saved = recorder.saveRoom("hall", p(0f, 0f))
        assertThrows(IllegalArgumentException::class.java) { HomeRecorder(saved, p(4f, 0f)) }
    }
    @Test fun poseJumpAndDifferentFloorStopRecording() {
        val recorder = HomeRecorder(HomeMap(), p(0f, 0f))
        assertThrows(IllegalArgumentException::class.java) { recorder.observe(p(3f, 0f)) }
        assertThrows(IllegalArgumentException::class.java) { recorder.observe(p(0f, 0f, 1f)) }
    }
    @Test fun duplicateRoomDoesNotOverwriteSavedLocation() {
        val recorder = HomeRecorder(HomeMap(), p(0f, 0f))
        val saved = recorder.saveRoom("kitchen", p(0f, 0f))
        assertThrows(IllegalArgumentException::class.java) { recorder.saveRoom("kitchen", p(.4f, 0f)) }
        assertEquals(p(0f, 0f), saved.nodes.first().point)
    }
    private fun aroundWallMap() = HomeMap(nodes = listOf(
        HomeNode(0, p(0f, 0f)), HomeNode(1, p(0f, 2f)), HomeNode(2, p(2f, 2f)), HomeNode(3, p(2f, 0f))
    ), edges = listOf(HomeEdge(0, 1), HomeEdge(1, 2), HomeEdge(2, 3)), rooms = listOf(HomeRoom("kitchen", 3)))

    @Test fun routingFollowsRecordedEdgesAroundWallRatherThanStraightToRoom() {
        val map = aroundWallMap()
        val route = HomeRouting.plan(map, p(0f, 0f), map.rooms.first())
        assertTrue(route.points.contains(p(0f, 2f)))
        assertTrue(route.points.contains(p(2f, 2f)))
        assertEquals(6f, route.points.zipWithNext().sumOf { (a, b) -> a.distance(b).toDouble() }.toFloat(), .01f)
    }
    @Test fun routingCanBeginMidwayOnARecordedSegmentAndRunInReverse() {
        val map = aroundWallMap().copy(rooms = listOf(HomeRoom("entrance", 0)))
        val route = HomeRouting.plan(map, p(2f, 1f), map.rooms.first())
        assertEquals(p(0f, 0f), route.points.last())
        assertEquals(5f, route.points.zipWithNext().sumOf { (a, b) -> a.distance(b).toDouble() }.toFloat(), .01f)
    }
    @Test fun routingRejectsUnrecordedSpaceAndDifferentFloors() {
        val map = aroundWallMap()
        assertThrows(IllegalArgumentException::class.java) { HomeRouting.plan(map, p(1f, 1f), map.rooms.first()) }
        assertThrows(IllegalArgumentException::class.java) { HomeRouting.plan(map, p(0f, 0f, 2f), map.rooms.first()) }
    }
    @Test fun followerSpeaksLeftRightStraightAndArrival() {
        val right = HomeFollower(HomeRoute("kitchen", listOf(p(0f, 0f), p(2f, 0f))))
        assertEquals(1, right.update(p(0f, 0f), 0f, 1f).turn)
        assertEquals(0, right.update(p(0f, 0f), 1f, 0f).turn)
        assertTrue(right.update(p(1.9f, 0f), 1f, 0f).arrived)
        val left = HomeFollower(HomeRoute("hall", listOf(p(0f, 0f), p(-2f, 0f))))
        assertEquals(-1, left.update(p(0f, 0f), 0f, 1f).turn)
    }
    @Test fun followerStopsOffPathInsteadOfJumpingToAnotherHallway() {
        val map = aroundWallMap()
        val follower = HomeFollower(HomeRouting.plan(map, p(0f, 0f), map.rooms.first()))
        assertTrue(follower.update(p(2f, .5f), 0f, 1f).offPath)
    }
    @Test fun followerRemainingDistanceUsesPathRatherThanStraightLine() {
        val map = aroundWallMap()
        val follower = HomeFollower(HomeRouting.plan(map, p(0f, 0f), map.rooms.first()))
        assertEquals(5f, follower.update(p(0f, 1f), 0f, 1f).remaining, .01f)
    }
    @Test fun homeMapRoundTripsAndInvalidFilesAreNotSilentlyReplaced() = runBlocking {
        val map = aroundWallMap()
        val bytes = ByteArrayOutputStream()
        HomeMapSerializer.writeTo(map, bytes)
        assertEquals(map, HomeMapSerializer.readFrom(ByteArrayInputStream(bytes.toByteArray())))
        assertThrows(androidx.datastore.core.CorruptionException::class.java) {
            runBlocking { HomeMapSerializer.readFrom(ByteArrayInputStream("invalid".toByteArray())) }
        }
        assertThrows(IllegalArgumentException::class.java) { map.copy(version = 99).validate() }
        assertThrows(IllegalArgumentException::class.java) { map.copy(edges = listOf(HomeEdge(0, 99))).validate() }
        Unit
    }
    @Test fun rotationOfDetectionBoxesMatchesArCoreViewCoordinates() {
        val identity = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f)
        val box = ArBoxMapping.map(floatArrayOf(0f, 0f, 100f, 200f), 90, 400f, 300f, identity)!!
        assertEquals(0f, box.left, .001f); assertEquals(.5f, box.right, .001f)
        assertEquals(2f / 3f, box.top, .001f); assertEquals(1f, box.bottom, .001f)
        val cropped = ArBoxMapping.map(floatArrayOf(0f, 0f, 400f, 300f), 0, 400f, 300f,
            floatArrayOf(-.1f, 0f, 1.1f, 0f, -.1f, 1f))!!
        assertEquals(0f, cropped.left, .001f); assertEquals(1f, cropped.right, .001f)
    }
}
