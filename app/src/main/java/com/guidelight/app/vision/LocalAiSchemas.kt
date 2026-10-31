package com.guidelight.app.vision

import kotlinx.serialization.json.*

/** Decoder constraints supplement, rather than replace, the checked output policy. */
object LocalAiSchemas {
    private fun choice(values: List<String>)=buildJsonObject {
        put("type","string");putJsonArray("enum"){values.forEach {add(it)}}
    }
    fun scene(references: List<AiRoomReference>)=buildJsonObject {
        put("type","object");put("additionalProperties",false)
        putJsonArray("required"){add("scene_type");add("objects");add("reference_match")}
        putJsonObject("properties") {
            put("scene_type",choice(LocalAiPolicy.scenes))
            putJsonObject("objects") {
                put("type","array");put("maxItems",3)
                putJsonObject("items") {
                    put("type","object");put("additionalProperties",false)
                    putJsonArray("required"){add("name");add("side")}
                    putJsonObject("properties") {
                        put("name",choice(LocalAiPolicy.objectNames));put("side",choice(LocalAiPolicy.sides))
                    }
                }
            }
            put("reference_match",if(references.isEmpty())buildJsonObject {put("type","null")}
                else buildJsonObject {putJsonArray("anyOf") {
                    add(choice(references.map {it.id}));add(buildJsonObject {put("type","null")})
                }})
        }
    }
}
