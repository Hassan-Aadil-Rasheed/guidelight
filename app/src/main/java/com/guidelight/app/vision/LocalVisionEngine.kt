package com.guidelight.app.vision

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.*
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

data class LocalAiResult(val content: String, val usedReasoning: Boolean, val elapsedMs: Long)

/** Official pinned llama.cpp runner, packaged read-only with the app. Loopback only,
 * random per-process key, no uploaded camera data and no downloaded executable code.
 */
class LocalVisionEngine(private val context: Context,val profile: LocalAiProfile=LocalAiProfile.DETAILED) : AutoCloseable {
    private val modelFiles=profile.files
    private val directory=File(context.filesDir,"local-ai").apply { mkdirs() }
    private val network=OkHttpClient.Builder().connectTimeout(3,TimeUnit.SECONDS).readTimeout(180,TimeUnit.SECONDS)
        .callTimeout(180,TimeUnit.SECONDS).build()
    private val health=network.newBuilder().callTimeout(2,TimeUnit.SECONDS).build()
    @Volatile private var process: Process? = null
    @Volatile private var activeCall: okhttp3.Call? = null
    @Volatile private var port=0
    private var key=""
    private var verified=false
    private val operation=Mutex()
    private val tail=java.util.concurrent.ConcurrentLinkedDeque<String>()
    val installed get() = modelFiles.all { File(directory,it.name).length()==it.bytes }

    suspend fun prepare(onStatus: (String)->Unit) = withContext(Dispatchers.IO) {
        operation.withLock {prepareLocked(onStatus)}
    }
    private suspend fun prepareLocked(onStatus: (String)->Unit) {
        for (entry in modelFiles) {
            currentCoroutineContext().ensureActive()
            val destination=File(directory,entry.name)
            if (destination.length()!=entry.bytes) download(entry,destination,onStatus)
            if (!verified) {
                onStatus("Checking the AI model…")
                val digest=MessageDigest.getInstance("SHA-256")
                destination.inputStream().buffered().use { input ->
                    val buffer=ByteArray(1024*1024)
                    while(true) { currentCoroutineContext().ensureActive(); val count=input.read(buffer);if(count<0)break;digest.update(buffer,0,count) }
                }
                if(digest.digest().joinToString("") { "%02x".format(it) }!=entry.hash) {
                    destination.delete()
                    error("AI model verification failed. Use set up local AI to download it again.")
                }
            }
        }
        verified=true
        start(onStatus)
    }

    private suspend fun download(entry: LocalModelFile, destination: File, onStatus: (String)->Unit) {
        require(directory.usableSpace > (entry.bytes-destination.length()).coerceAtLeast(0)+100_000_000L) { "More free storage is needed for local AI." }
        val partial=File(destination.path+".part")
        var offset=partial.length()
        val request=Request.Builder().url("https://huggingface.co/${profile.repository}/resolve/${profile.revision}/${entry.name}")
            .apply { if(offset>0) header("Range","bytes=$offset-") }.build()
        val client=network.newBuilder().callTimeout(0,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).build()
        val call=client.newCall(request); activeCall=call
        call.execute().use { response ->
            require(response.isSuccessful) { "AI model download failed. Check internet and retry." }
            if (offset>0 && response.code!=206) offset=0
            val body=response.body ?: error("Empty model download")
            body.byteStream().use { input -> java.io.FileOutputStream(partial,offset>0).use { output ->
                val buffer=ByteArray(128*1024);var count=offset;var last=0L
                while(true) {
                    currentCoroutineContext().ensureActive()
                    val read=input.read(buffer);if(read<0)break
                    output.write(buffer,0,read);count+=read
                    require(count<=entry.bytes) { "AI download has an unexpected size." }
                    if(SystemClock.elapsedRealtime()-last>500) {onStatus("Installing AI: ${count*100/entry.bytes}%");last=SystemClock.elapsedRealtime()}
                }
            } }
        }
        activeCall=null
        require(partial.length()==entry.bytes && partial.renameTo(destination)) { "AI model download was interrupted. Retry setup." }
    }

    private suspend fun start(onStatus: (String)->Unit) {
        if (process?.isAlive==true && port!=0) return
        close();tail.clear()
        onStatus("Loading ${profile.label.lowercase()}…")
        port=ServerSocket(0).use { it.localPort };key=UUID.randomUUID().toString()
        val lib=context.applicationInfo.nativeLibraryDir
        val arguments=listOf(File(lib,"libguidelight_lfm_server.so").path,
            "--model",File(directory,modelFiles[0].name).path,"--mmproj",File(directory,modelFiles[1].name).path,
            "--host","127.0.0.1","--port",port.toString(),"--api-key",key,
            "--ctx-size","2048","--parallel","1","--threads","2","--threads-batch","2",
            "--batch-size","128","--ubatch-size","64","--gpu-layers","0","--no-mmproj-offload",
            "--image-min-tokens","64","--image-max-tokens","96","--no-warmup","--no-webui") +
            if(profile.thinking)listOf("--reasoning","on","--reasoning-budget","96","--reasoning-format","deepseek","--no-reasoning-preserve")
            else listOf("--reasoning","off")
        val child=ProcessBuilder(arguments).apply { environment()["LD_LIBRARY_PATH"]=lib;redirectErrorStream(true) }.start()
        process=child
        Thread({ runCatching {child.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
            if (line.length<1500) { tail.addLast(line);while(tail.size>16)tail.pollFirst() }
        } } } },"local-ai-log").apply { isDaemon=true;start() }
        val until=SystemClock.elapsedRealtime()+120_000
        while(SystemClock.elapsedRealtime()<until) {
            currentCoroutineContext().ensureActive()
            check(child.isAlive) { "The AI runtime could not load this model on the phone." }
            val ready=runCatching { health.newCall(Request.Builder().url("http://127.0.0.1:$port/health")
                .header("Authorization","Bearer $key").build()).execute().use { it.isSuccessful } }.getOrDefault(false)
            if(ready) return
            delay(300)
        }
        close();error("The AI model took too long to load. Try again after closing other apps.")
    }

    suspend fun reason(system: String, question: String, images: List<ByteArray>, onStatus: (String)->Unit,
        schema: JsonObject?=null): LocalAiResult = withContext(Dispatchers.IO) {
        operation.withLock {
        prepareLocked(onStatus)
        val started=SystemClock.elapsedRealtime()
        onStatus(if(profile.thinking)"Thinking about ${if(images.isEmpty()) "your status" else "the camera view"}…" else "Reading the camera view…")
        val content=buildJsonArray {
            images.forEach { image -> add(buildJsonObject { put("type","image_url");putJsonObject("image_url") {
                put("url","data:image/jpeg;base64,"+Base64.encodeToString(image,Base64.NO_WRAP))
            } }) }
            add(buildJsonObject {put("type","text");put("text",question)})
        }
        val payload=buildJsonObject {
            putJsonArray("messages") {
                add(buildJsonObject {put("role","system");put("content",system)})
                add(buildJsonObject {put("role","user");put("content",content)})
            }
            put("temperature",.1);put("top_p",1.0);put("top_k",50);put("min_p",0.0);put("repeat_penalty",1.1)
            put("max_tokens",if(profile.thinking)320 else 160);put("stream",false)
            putJsonObject("chat_template_kwargs") {put("enable_thinking",profile.thinking)}
            if(schema!=null)putJsonObject("response_format") {
                put("type","json_schema");putJsonObject("json_schema") {
                    put("name","camera_scene");put("strict",true);put("schema",schema)
                }
            }
        }
        val client=if(profile.thinking)network else network.newBuilder().callTimeout(45,TimeUnit.SECONDS).build()
        val call=client.newCall(Request.Builder().url("http://127.0.0.1:$port/v1/chat/completions")
            .header("Authorization","Bearer $key").post(payload.toString().toRequestBody("application/json".toMediaType())).build())
        activeCall=call
        try { call.execute().use { response ->
            check(response.isSuccessful) { "The AI could not process this request. Try a simpler view." }
            val answer=LocalAiReply.parse(response.body?.string() ?: "{}",profile.thinking)
            LocalAiResult(answer.content,answer.usedReasoning,SystemClock.elapsedRealtime()-started)
        } } finally { activeCall=null }
        }
    }
    override fun close() {
        activeCall?.cancel();activeCall=null
        val child=process;process=null;port=0
        child?.destroy()
        if(child!=null)Thread({runCatching {if(!child.waitFor(2,TimeUnit.SECONDS))child.destroyForcibly()}},"local-ai-stop")
            .apply {isDaemon=true;start()}
    }
}
