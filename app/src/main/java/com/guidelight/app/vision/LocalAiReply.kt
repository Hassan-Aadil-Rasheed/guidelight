package com.guidelight.app.vision

import kotlinx.serialization.json.*

data class AiModelAnswer(val content: String, val usedReasoning: Boolean)

object LocalAiReply {
    fun parse(body: String,requireReasoning: Boolean=true): AiModelAnswer {
        val result=Json.parseToJsonElement(body).jsonObject
        val message=result["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: error("No AI answer returned.")
        val answer=message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val thinking=message["reasoning_content"]?.jsonPrimitive?.contentOrNull.orEmpty()
        check(!requireReasoning || thinking.isNotBlank()) { "The model did not complete its reasoning. Try again." }
        check(answer.isNotBlank()) { "The AI did not finish an answer. Try a simpler view." }
        return AiModelAnswer(answer,thinking.isNotBlank())
    }
}
