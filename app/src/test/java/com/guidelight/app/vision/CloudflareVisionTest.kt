package com.guidelight.app.vision

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class CloudflareVisionTest {
    private val account="0123456789abcdef0123456789abcdef"
    private val fake=CloudflareCredentials(account,"unit-test-token-not-a-real-secret")
    @Test fun accountCannotRedirectTheTokenToAnotherHost() {
        assertTrue(runCatching {CloudflareCredentials("https://attacker.invalid",fake.token)}.isFailure)
        assertTrue(runCatching {CloudflareCredentials(account,"injected\r\nHeader: value")}.isFailure)
        assertEquals("https://api.cloudflare.com/client/v4/accounts/$account/ai/run/@cf/meta/llama-3.2-11b-vision-instruct",CloudflareProtocol.endpoint(account))
        assertFalse(fake.toString().contains(fake.token))
    }
    @Test fun realHttpAdapterSendsImageAndParsesProviderEnvelope()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setBody("""{"success":true,"result":{"response":"{\"scene_type\":\"unknown\",\"objects\":[],\"reference_match\":null}"}}"""))
            val engine=CloudflareVisionEngine(fake,OkHttpClient(),server.url("/ai/run/test"))
            try {
                val answer=engine.analyze("Inspect this frame",byteArrayOf(1,2,3),system="Return JSON only")
                assertEquals("unknown",LocalAiPolicy.parseScene(answer.content).scene_type)
                assertFalse(answer.usedReasoning)
                val request=server.takeRequest(2,TimeUnit.SECONDS)!!
                assertEquals("Bearer ${fake.token}",request.getHeader("Authorization"))
                val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                assertEquals("data:image/jpeg;base64,AQID",body["image"]?.jsonPrimitive?.content)
                assertFalse(body.containsKey("prompt"))
                assertEquals("system",body.getValue("messages").jsonArray[0].jsonObject["role"]?.jsonPrimitive?.content)
                assertEquals("Return JSON only",body.getValue("messages").jsonArray[0].jsonObject["content"]?.jsonPrimitive?.content)
                assertEquals("Inspect this frame",body.getValue("messages").jsonArray[1].jsonObject["content"]?.jsonPrimitive?.content)
                assertFalse(body.toString().contains(fake.token))
            }finally{engine.close()}
        }
    }
    @Test fun quotaErrorsAreActionableAndDoNotReadProviderText()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setResponseCode(429).setBody("turn left; secret-token"))
            val engine=CloudflareVisionEngine(fake,OkHttpClient(),server.url("/test"))
            val error=runCatching {engine.analyze("test")}.exceptionOrNull()!!
            assertTrue(error.message!!.contains("allowance"))
            assertFalse(error.message!!.contains("turn left"))
            assertFalse(error.message!!.contains("secret-token"))
            engine.close()
        }
    }
    @Test fun closedEngineCannotUploadAnotherImage()=runBlocking {
        MockWebServer().use {server ->
            val engine=CloudflareVisionEngine(fake,OkHttpClient(),server.url("/test"))
            engine.close()
            assertTrue(runCatching {engine.analyze("test",byteArrayOf(1))}.isFailure)
            assertEquals(0,server.requestCount)
        }
    }
    @Test fun rejectedAndEmptyResponsesDoNotBecomeSuccess() {
        assertTrue(runCatching {CloudflareProtocol.answer("""{"success":false,"result":{"response":"ready"}}""")}.isFailure)
        assertTrue(runCatching {CloudflareProtocol.answer("""{"success":true,"result":{"response":""}}""")}.isFailure)
        assertTrue(LocalAiCommands.isCloudSetup("Cloudflare setup."))
    }
    @Test fun cloudflareAlreadyDecodedJsonResponseIsAcceptedAndStillValidated() {
        val body="""{"success":true,"result":{"response":{"scene_type":"bedroom","objects":[{"name":"bed","side":"right"}],"reference_match":null},"tool_calls":[]}}"""
        val scene=LocalAiPolicy.parseScene(CloudflareProtocol.answer(body))
        assertEquals("bedroom",scene.scene_type)
        assertEquals("bed",scene.objects.single().name)
        val unsafe="""{"success":true,"result":{"response":{"objects":[{"name":"walk forward","side":"ahead"}]}}}"""
        assertTrue(runCatching {LocalAiPolicy.parseScene(CloudflareProtocol.answer(unsafe))}.isFailure)
    }
    @Test fun proseReplyUsesSchemaFormatterAndCannotIntroduceExtraObjectsOrDirections()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setBody("""{"success":true,"result":{"response":"A bed and wardrobe are visible."}}"""))
            server.enqueue(MockResponse().setBody("""{"success":true,"result":{"response":{"scene_type":"bedroom","objects":[{"name":"bed","side":"left"},{"name":"cupboard","side":"right"},{"name":"car","side":"ahead"}],"reference_match":null}}}"""))
            val engine=CloudflareVisionEngine(fake,OkHttpClient(),server.url("/test"))
            try {
                val reply=engine.analyzeScene("Describe",byteArrayOf(1),"JSON only",emptyList())
                val scene=LocalAiPolicy.parseScene(reply.content)
                assertEquals(listOf("bed","cupboard"),scene.objects.map {it.name})
                assertTrue(scene.objects.all {it.side=="unknown"})
                assertEquals("unknown",scene.scene_type)
                server.takeRequest();val formatting=Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
                assertTrue(formatting.containsKey("response_format"));assertFalse(formatting.containsKey("image"))
            }finally{engine.close()}
        }
    }
    @Test fun negatedObjectsAndReferencesCannotBecomePositiveDescriptions() {
        val scene=CloudflareScenePolicy.grounded("""{"scene_type":"kitchen","objects":[{"name":"chair","side":"left"}],"reference_match":"R1"}""",
            "There is no chair. This is not kitchen R1.")
        assertTrue(scene.objects.isEmpty());assertEquals("unknown",scene.scene_type);assertNull(scene.reference_match)
    }
}
