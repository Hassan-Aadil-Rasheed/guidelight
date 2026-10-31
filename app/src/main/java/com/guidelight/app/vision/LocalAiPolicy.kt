package com.guidelight.app.vision

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class LocalAiTask { DESCRIBE, IDENTIFY_ROOM, EXPLAIN_STATUS, DETAILED_VIEW }
@Serializable data class AiObject(val name: String, val side: String = "unknown")
@Serializable data class AiScene(val scene_type: String = "unknown", val objects: List<AiObject> = emptyList(),
    val reference_match: String? = null)
data class AiRoomReference(val id: String, val name: String)

/** Model observations are never used to create unverified walking instructions. */
object LocalAiPolicy {
    private val json = Json { ignoreUnknownKeys = true }
    val objectNames = listOf("chair","table","bed","sofa","door","window","stairs","person","car","bicycle",
        "cupboard","sink","toilet","stove","fridge","desk","bag","box","wall","floor","curtain","shelf","plant","laptop","monitor","unknown")
    val scenes = listOf("kitchen","bedroom","bathroom","hall","living room","dining room","balcony","office","outdoors","unknown")
    val sides = listOf("left","ahead","right","unknown")
    fun failureMessage(error: Exception): String = when(error) {
        is kotlinx.serialization.SerializationException -> "The AI answer was incomplete or could not be verified. Try again."
        is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> "Local AI took too long to answer. Try a simpler view."
        is java.io.IOException -> "The local AI connection was interrupted. Try again."
        is IllegalStateException, is IllegalArgumentException -> error.message?.takeIf { it.length in 1..240 }
            ?: "The local AI could not finish this analysis. Try again."
        else -> "The local AI could not finish this analysis. Try again."
    }
    fun parseScene(content: String): AiScene {
        val start=content.indexOf('{'); val end=content.lastIndexOf('}')
        require(start>=0 && end>=start) { "The AI did not finish its answer. Try again." }
        val scene=json.decodeFromString<AiScene>(content.substring(start,end+1))
        require(scene.scene_type in scenes && scene.objects.size<=8 &&
            scene.objects.all { it.name in objectNames && it.side in sides }) { "The AI answer could not be verified." }
        return scene
    }
    fun describe(scene: AiScene, references: List<AiRoomReference>, verifiedRoom: String?): String {
        val statements=mutableListOf<String>()
        if (scene.scene_type != "unknown") statements += "The image looks like a ${scene.scene_type}."
        val objects=scene.objects.filter { it.name!="unknown" }.distinct().take(3)
        if (objects.isNotEmpty()) statements += "The AI identifies " + objects.joinToString(", ") {
            "a ${it.name}" + when(it.side) { "left" -> " on the camera's left"; "right" -> " on the camera's right"; "ahead" -> " ahead"; else -> "" }
        } + "."
        val reference=references.firstOrNull { it.id==scene.reference_match }
        if (scene.reference_match != null && reference==null) throw IllegalArgumentException("The AI selected an unknown room reference.")
        if (reference != null) {
            statements += if (verifiedRoom!=null && reference.name!=verifiedRoom) "The image comparison conflicts with the tracked room. I cannot confirm a room from this image."
                else if (reference.name==verifiedRoom) "Your position also matches the saved ${reference.name} view."
                else "The image resembles your saved ${reference.name} view. Your position is not verified by this comparison."
        }
        if (statements.isEmpty()) statements += "The AI could not identify this view. Try another angle with more light."
        return statements.joinToString(" ")
    }
    fun statusAnswer(content: String, statusKey: String, verifiedRoom: String? = null): String {
        val start=content.indexOf('{');val end=content.lastIndexOf('}')
        require(start>=0 && end>=start) {"The AI did not finish its status answer."}
        val value=json.parseToJsonElement(content.substring(start,end+1))
            as? kotlinx.serialization.json.JsonObject ?: error("The AI did not finish its answer.")
        val key=(value["status"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        val answer=(value["answer"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
        require(key==statusKey && answer.length in 1..600) { "The AI status answer could not be verified." }
        require(!Regex("\\b(walk|turn|cross|proceed|step|move|head|go|safe|clear path|meters?|metres?|centimetres?|millimetres?)\\b",RegexOption.IGNORE_CASE).containsMatchIn(answer)) {
            "The AI tried to give unverified movement advice. Ask for your location or describe the view instead."
        }
        // Spoken location facts come from the app's checked state, not generated prose.
        return when(statusKey) {
            "localized" -> {require(!verifiedRoom.isNullOrBlank());"Your location matches the saved $verifiedRoom view. No room walking route is active."}
            "recording" -> "A home map is being recorded. Room recognition is checked after saving."
            "no_map" -> "No saved home map is available yet."
            "tracking_unavailable" -> "Camera tracking is unavailable, so your position is not verified."
            "not_localized" -> "Your home map is saved, but your current position is not verified."
            else -> throw IllegalArgumentException("The AI status answer could not be verified.")
        }
    }
}
