package com.guidelight.app.indoor

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class HomePoint(val x: Float, val y: Float, val z: Float) {
    fun distance(other: HomePoint): Float = hypot(x - other.x, z - other.z)
    fun valid() = listOf(x, y, z).all { it.isFinite() && abs(it) <= 100f }
}

@Serializable data class HomeNode(val id: Int, val point: HomePoint)
@Serializable data class HomeEdge(val from: Int, val to: Int)
@Serializable data class HomeRoom(val name: String, val node: Int)

object HomeMarkers {
    const val ORIGINAL = "home-earth-18cm"
    const val PERSONAL = "home-earth-personal-18cm"
    val supported = setOf(ORIGINAL, PERSONAL)
}

@Serializable
data class HomeMap(
    val version: Int = 1,
    val marker: String = "home-earth-18cm",
    val nodes: List<HomeNode> = emptyList(),
    val edges: List<HomeEdge> = emptyList(),
    val rooms: List<HomeRoom> = emptyList()
) {
    fun validate(): HomeMap {
        require(version == 1 && marker in HomeMarkers.supported) { "Home map version is unsupported." }
        require(nodes.size <= 3000 && rooms.size <= 20 && edges.size <= 6000)
        val ids = nodes.map { it.id }.toSet()
        require(ids.size == nodes.size && nodes.all { it.id >= 0 && it.point.valid() })
        require(edges.all { it.from in ids && it.to in ids && it.from != it.to })
        require(rooms.all { it.node in ids && HomeCommands.roomName(it.name) == it.name })
        require(rooms.map { it.name }.distinct().size == rooms.size)
        return this
    }
}

/** A single assisted recording. New branches start at the saved entrance, never across a wall. */
class HomeRecorder(map: HomeMap, start: HomePoint) {
    private val original = map.validate()
    private val nodes = original.nodes.toMutableList()
    private val edges = original.edges.toMutableList()
    private val rooms = original.rooms.toMutableList()
    private var cursor: HomeNode
    private var lastObserved = start

    init {
        require(start.valid())
        if (nodes.isEmpty()) {
            require(hypot(start.x, start.z) <= 1.5f) { "Start recording near the fixed home marker, with your helper." }
            cursor = HomeNode(0, start)
            nodes += cursor
        } else {
            cursor = nodes.first()
            require(start.distance(cursor.point) <= .6f && abs(start.y - cursor.point.y) <= .4f) {
                "Return to the recording start near the marker before recording a new path."
            }
            if (start.distance(cursor.point) >= .1f) append(start)
        }
    }

    fun observe(point: HomePoint) {
        require(point.valid()) { "Tracking position is invalid. Scan the marker again." }
        require(point.distance(lastObserved) <= 1f) { "Tracking moved unexpectedly. Recording stopped. Scan the marker again." }
        require(abs(point.y - nodes.first().point.y) <= .65f) { "Record one floor at a time and keep the phone at chest height." }
        lastObserved = point
        if (point.distance(cursor.point) >= .3f) append(point)
    }

    fun saveRoom(name: String, point: HomePoint): HomeMap {
        require(HomeCommands.roomName(name) == name) { "Use a short room name." }
        require(rooms.none { it.name == name }) { "$name is already saved. Existing rooms were kept." }
        require(rooms.size < 20) { "This prototype supports up to 20 rooms." }
        observe(point)
        if (point.distance(cursor.point) >= .05f) append(point)
        rooms += HomeRoom(name, cursor.id)
        return snapshot()
    }

    /** Current actually walked graph, including the unsaved tail. Only saveRoom commits to disk. */
    fun snapshot(): HomeMap = HomeMap(marker = original.marker, nodes = nodes.toList(), edges = edges.toList(), rooms = rooms.toList()).validate()

    private fun append(point: HomePoint) {
        require(nodes.size < 3000) { "This home recording is full. Finish recording." }
        val next = HomeNode((nodes.maxOfOrNull { it.id } ?: -1) + 1, point)
        edges += HomeEdge(cursor.id, next.id)
        nodes += next
        cursor = next
    }
}
