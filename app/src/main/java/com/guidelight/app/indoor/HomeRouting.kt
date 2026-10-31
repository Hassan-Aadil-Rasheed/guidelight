package com.guidelight.app.indoor

import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleWeightedGraph
import kotlin.math.*

data class HomeRoute(val room: String, val points: List<HomePoint>)

object HomeRouting {
    /** Only recorded edges connect rooms. Nearby points are never joined through unrecorded space. */
    fun plan(map: HomeMap, position: HomePoint, room: HomeRoom): HomeRoute {
        map.validate()
        require(position.valid())
        val nodes = map.nodes.associateBy { it.id }
        require(room in map.rooms && room.node in nodes) { "That room is not saved." }
        if (map.edges.isEmpty()) {
            val target = nodes.getValue(room.node).point
            require(position.distance(target) <= .35f && abs(position.y - target.y) <= .4f) { "Return to a recorded path with assistance, then try again." }
            return HomeRoute(room.name, listOf(position, target))
        }
        val nearest = map.edges.map { edge ->
            val a = nodes.getValue(edge.from).point
            val b = nodes.getValue(edge.to).point
            edge to project(position, a, b)
        }.filter { abs(it.second.y - position.y) <= .4f }.minByOrNull { position.distance(it.second) }
        require(nearest != null && position.distance(nearest.second) <= .45f) {
            "You are away from the recorded path. Return with assistance, then choose the room again."
        }
        val graph = SimpleWeightedGraph<Int, DefaultWeightedEdge>(DefaultWeightedEdge::class.java)
        nodes.keys.forEach(graph::addVertex)
        map.edges.forEach { edge ->
            graph.addEdge(edge.from, edge.to)?.let {
                graph.setEdgeWeight(it, nodes.getValue(edge.from).point.distance(nodes.getValue(edge.to).point).toDouble().coerceAtLeast(.001))
            }
        }
        graph.addVertex(-1)
        listOf(nearest.first.from, nearest.first.to).forEach { end ->
            graph.setEdgeWeight(graph.addEdge(-1, end), nearest.second.distance(nodes.getValue(end).point).toDouble().coerceAtLeast(.001))
        }
        val path = DijkstraShortestPath(graph).getPath(-1, room.node)
            ?: throw IllegalArgumentException("No recorded path connects to that room.")
        val points = (listOf(position, nearest.second) + path.vertexList.drop(1).map { nodes.getValue(it).point })
            .fold(mutableListOf<HomePoint>()) { result, point ->
                if (result.lastOrNull()?.distance(point)?.let { it >= .02f } != false) result += point
                result
            }
        return HomeRoute(room.name, points)
    }

    fun project(point: HomePoint, a: HomePoint, b: HomePoint): HomePoint {
        val dx = b.x - a.x; val dz = b.z - a.z
        val length = dx * dx + dz * dz
        val t = if (length < .000001f) 0f else (((point.x - a.x) * dx + (point.z - a.z) * dz) / length).coerceIn(0f, 1f)
        return HomePoint(a.x + t * dx, a.y + t * (b.y - a.y), a.z + t * dz)
    }
}

data class HomeProgress(val instruction: String, val remaining: Float, val turn: Int = 0, val arrived: Boolean = false, val offPath: Boolean = false)

/** Advance along adjacent recorded segments only; do not snap to a later hallway across a wall. */
class HomeFollower(private val route: HomeRoute) {
    private var index = 0
    fun update(position: HomePoint, forwardX: Float, forwardZ: Float): HomeProgress {
        val points = route.points
        if (points.isEmpty() || !position.valid() || !forwardX.isFinite() || !forwardZ.isFinite())
            return HomeProgress("Stop. Tracking is unavailable. Scan the marker again.", 0f, offPath = true)
        if (points.size == 1) return HomeProgress("You have reached ${route.room}.", 0f, arrived = true)
        while (index < points.lastIndex - 1 && position.distance(points[index + 1]) <= .25f) index++
        val closest = HomeRouting.project(position, points[index], points[index + 1])
        if (position.distance(closest) > .65f || abs(position.y - closest.y) > .45f)
            return HomeProgress("Stop. You are off the recorded path. Return with assistance and start again.", 0f, offPath = true)
        val remaining = closest.distance(points[index + 1]) + (index + 1 until points.lastIndex).sumOf {
            points[it].distance(points[it + 1]).toDouble()
        }.toFloat()
        if (index == points.lastIndex - 1 && position.distance(points.last()) <= .3f)
            return HomeProgress("You have reached ${route.room}.", 0f, arrived = true)
        if (hypot(forwardX, forwardZ) < .4f)
            return HomeProgress("Hold the phone upright, facing the way you walk.", remaining)
        val target = points[index + 1]
        var angle = Math.toDegrees((atan2(target.x - position.x, target.z - position.z) - atan2(forwardX, forwardZ)).toDouble())
        while (angle > 180) angle -= 360
        while (angle < -180) angle += 360
        val turn = if (angle > 30) 1 else if (angle < -30) -1 else 0
        val instruction = when {
            abs(angle) >= 120 -> "Stop and turn around slowly."
            turn > 0 -> "Turn right along the recorded path."
            turn < 0 -> "Turn left along the recorded path."
            else -> "Continue ahead along the recorded path."
        }
        return HomeProgress(instruction, remaining, turn)
    }
}
