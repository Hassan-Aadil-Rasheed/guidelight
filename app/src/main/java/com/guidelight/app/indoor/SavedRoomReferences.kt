package com.guidelight.app.indoor

import kotlin.math.abs
import kotlin.math.sqrt

data class SavedViewNode(val id: Int, val stamp: Double, val map: Int, val weight: Int,
    val x: Float, val y: Float, val z: Float)

/** Rebind labels on discarded nodes only to an adjacent retained view at the same recorded spot.
 * This does not infer a room from a visually matched node elsewhere in the map.
 */
object SavedRoomReferences {
    fun resolve(anchor: SavedViewNode, nodes: List<SavedViewNode>): Int? {
        fun finite(node: SavedViewNode) = node.stamp.isFinite() && listOf(node.x,node.y,node.z).all { it.isFinite() }
        if (!finite(anchor)) return null
        if (anchor.weight >= 0) return anchor.id
        return nodes.asSequence().filter { candidate ->
            candidate.weight >= 0 && candidate.map == anchor.map && finite(candidate) &&
                abs(candidate.stamp-anchor.stamp) <= 5.0 &&
                sqrt((candidate.x-anchor.x)*(candidate.x-anchor.x) +
                    (candidate.y-anchor.y)*(candidate.y-anchor.y) +
                    (candidate.z-anchor.z)*(candidate.z-anchor.z)) <= .25f
        }.minWithOrNull(compareBy<SavedViewNode> { abs(it.stamp-anchor.stamp) }.thenBy { it.id })?.id
    }
    fun rebind(rooms: List<SavedRoomView>, nodes: List<SavedViewNode>): List<SavedRoomView> {
        val byId = nodes.associateBy { it.id }
        val rebound = rooms.mapNotNull { room -> byId[room.node]?.let { anchor ->
            resolve(anchor,nodes)?.let { SavedRoomView(it,room.room) }
        } }
        // Two different labels on one view are ambiguous; retain neither as a room fix.
        return rebound.groupBy { it.node }.values.filter { group -> group.map { it.room }.distinct().size == 1 }
            .map { it.first() }
    }
}
