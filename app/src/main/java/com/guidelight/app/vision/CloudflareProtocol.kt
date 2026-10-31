package com.guidelight.app.vision

import kotlinx.serialization.json.*
import java.util.Base64

class CloudflareCredentials(val accountId: String,val token: String) {
    init {CloudflareProtocol.validate(accountId,token)}
    override fun toString()="CloudflareCredentials(redacted)"
}

object CloudflareProtocol {
    const val model="@cf/meta/llama-3.2-11b-vision-instruct"
    const val formatModel="@cf/meta/llama-3.1-8b-instruct"
    fun validate(account: String,token: String) {
        require(account.matches(Regex("[a-fA-F0-9]{32}"))) {"Enter the 32-character Cloudflare Account ID."}
        require(token.matches(Regex("[A-Za-z0-9_-]{20,512}"))) {"Enter a valid Workers AI API token."}
    }
    fun endpoint(account: String): String {
        require(account.matches(Regex("[a-fA-F0-9]{32}")))
        return "https://api.cloudflare.com/client/v4/accounts/$account/ai/run/$model"
    }
    const val defaultSystem="Follow the user's requested response format exactly. Return only the requested answer, without explanations or markdown."
    fun request(prompt: String,image: ByteArray?=null,system: String=defaultSystem)=buildJsonObject {
        putJsonArray("messages") {
            add(buildJsonObject {put("role","system");put("content",system)})
            add(buildJsonObject {put("role","user");put("content",prompt)})
        }
        put("max_tokens",220);put("temperature",.1);put("stream",false)
        if(image!=null)put("image","data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(image))
    }
    fun answer(body: String): String {
        val value=Json.parseToJsonElement(body).jsonObject
        check(value["success"]?.jsonPrimitive?.booleanOrNull!=false) {"Cloudflare rejected the request. Check model access and your free allowance."}
        val result=value["result"] ?: error("Cloudflare returned no answer.")
        val text=when(result) {
            is JsonPrimitive -> result.contentOrNull
            is JsonObject -> when(val response=result["response"]) {
                is JsonPrimitive -> response.contentOrNull
                is JsonObject -> response.toString()
                else -> null
            }
            else -> null
        }
        return text?.takeIf {it.isNotBlank() && it.length<=8000} ?: error("Cloudflare returned an incomplete answer.")
    }
    fun httpError(code: Int)=when(code) {
        401,403 -> "Cloudflare access was denied. Check the token, account and model license in Cloudflare setup."
        429 -> "Cloudflare's rate limit or free allowance has been reached. Try later or use local mode."
        408,504 -> "Cloudflare took too long to answer. Try again or use local mode."
        502,503 -> "Cloudflare is busy or unavailable. Try again or use local mode."
        else -> "Cloudflare could not process this request. Check model access in Cloudflare setup."
    }
}
