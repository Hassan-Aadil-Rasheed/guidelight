package com.guidelight.app.vision

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.guidelight.app.domain.*
import com.guidelight.app.indoor.HomeAppVoiceCommands
import com.guidelight.app.indoor.HomeVoiceDialog
import com.guidelight.app.indoor.HomeVoiceInput
import com.guidelight.app.ui.GuideLightTheme
import com.guidelight.app.ui.VoiceKeyboard
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.Locale

class CloudflareSetupActivity : ComponentActivity() {
    private var engine: CloudflareVisionEngine? = null
    private var job: Job? = null
    private var busy by mutableStateOf(false)
    private var status by mutableStateOf("")
    private var account by mutableStateOf("")
    private var token by mutableStateOf("")
    private var accepted by mutableStateOf(false)
    private var existing by mutableStateOf<CloudflareCredentials?>(null)
    private lateinit var store: CloudflareConfigStore
    private lateinit var voice: HomeVoiceInput
    private var speaker: TextToSpeech? = null
    private var speechReady = false

    private fun say(message: String) {
        status = message
        if (speechReady) speaker?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "cloud-status")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = androidx.activity.SystemBarStyle.dark(Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        store = CloudflareConfigStore(this)
        existing = store.read(); account = existing?.accountId.orEmpty(); accepted = existing != null
        status = if (existing == null) "Connect your Cloudflare account" else "Cloudflare is connected"
        speaker = TextToSpeech(this) { result ->
            speechReady = result == TextToSpeech.SUCCESS
            if (speechReady) { speaker?.language = Locale.US; say("$status. Say what can I say for setup commands.") }
        }
        voice = HomeVoiceInput(this, available = { true }, silence = { speaker?.stop() },
            vibrate = { duration -> getSystemService(Vibrator::class.java)?.let { if (it.hasVibrator()) it.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)) } },
            onCommand = ::command, feedback = ::say)
        setContent { GuideLightTheme {
            Scaffold(topBar = { TopAppBar(title = { Text("Cloudflare setup") }, navigationIcon = {
                IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Home") }
            }) }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                    .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = voice::arm, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                        Icon(Icons.Outlined.Mic, null); Spacer(Modifier.width(8.dp)); Text("Speak a command")
                    }
                    Text("Describe camera views online", style = MaterialTheme.typography.headlineSmall)
                    Text("Requested camera images and saved-room comparisons are sent to Cloudflare. No continuous video is uploaded. Internet and your account's free allowance are required.")
                    Text("In Cloudflare: Workers AI → Use REST API → Create a Workers AI API Token. Copy the token and Account ID.")
                    TextButton(onClick = { open(INSTRUCTIONS) }, enabled = !busy) { Text("Open setup instructions") }
                    OutlinedTextField(account, { account = it.trim() }, label = { Text("Account ID") }, singleLine = true, enabled = !busy,
                        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii))
                    OutlinedTextField(token, { token = it.trim() }, label = { Text("Workers AI API token") }, singleLine = true, enabled = !busy,
                        modifier = Modifier.fillMaxWidth(), supportingText = { Text(if (existing == null) "Stored encrypted on this phone" else "Leave blank to keep your saved token") },
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password))
                    Row(Modifier.fillMaxWidth().toggleable(value = accepted, enabled = !busy, role = Role.Checkbox, onValueChange = { accepted = it }),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(accepted, null, enabled = !busy); Text("I accept Meta's Llama license and acceptable use policy.")
                    }
                    Row {
                        TextButton(onClick = { open(LICENSE) }, enabled = !busy) { Text("License") }
                        TextButton(onClick = { open(USE_POLICY) }, enabled = !busy) { Text("Use policy") }
                    }
                    Button(onClick = ::connectAndTest, enabled = accepted && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Connect and test") }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(status, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    if (existing != null) OutlinedButton(onClick = ::disconnect, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Disconnect Cloudflare") }
                }
            }
            if (voice.state.armed) HomeVoiceDialog(this, voice.state.request, voice::changeText, voice::submit,
                voice::handleRemoteKey, { voice.cancel() }, hideKeyboard = voice.state.hideKeyboard)
        } }
    }

    private fun command(text: String) {
        VoiceKeyboard.hide(this)
        CloudSetupCommands.resolve(text)?.let { action ->
            if (busy && action != CloudSetupAction.CANCEL) { say("A connection test is running. Say cancel connection first."); return }
            when (action) {
                CloudSetupAction.CONNECT -> connectAndTest()
                CloudSetupAction.DISCONNECT -> disconnect()
                CloudSetupAction.INSTRUCTIONS -> open(INSTRUCTIONS)
                CloudSetupAction.LICENSE -> open(LICENSE)
                CloudSetupAction.USE_POLICY -> open(USE_POLICY)
                CloudSetupAction.ACCEPT_TERMS -> { accepted = true; say("License and use policy selected. Say connect and test when your account details are entered.") }
                CloudSetupAction.DECLINE_TERMS -> { accepted = false; say("License agreement cleared. Cloudflare connection is disabled.") }
                CloudSetupAction.CANCEL -> { engine?.close(); job?.cancel(); busy = false; say("Connection test cancelled.") }
            }
            return
        }
        when (AppVoiceCommands.resolve(text)) {
            AppVoiceAction.CLOSE, AppVoiceAction.BACK -> { finish(); return }
            AppVoiceAction.CLOUD_SETTINGS -> { say("Cloudflare settings. $status"); return }
            else -> Unit
        }
        if (!HomeAppVoiceCommands.handle(this, text, voice, ::say, CloudSetupCommands.help, { status })) say(CloudSetupCommands.help)
    }

    private fun disconnect() {
        if (busy) { say("Cancel the connection test before disconnecting."); return }
        store.clear(); existing = null; token = ""; accepted = false
        say("Disconnected. Home will use local mode.")
    }

    private fun connectAndTest() {
        if (busy) return
        if (!accepted) { say("Review the license and use policy, then explicitly accept them before connecting."); return }
        val credentials = runCatching { CloudflareCredentials(account,
            if (token.isBlank() && account == existing?.accountId) existing!!.token else token)
        }.getOrElse { say(it.message ?: "Check the account and token."); return }
        busy = true; say("Connecting and testing a generated image…")
        job = lifecycleScope.launch {
            val test = CloudflareVisionEngine(credentials); engine = test
            try {
                test.acceptLicense()
                val jpeg = withContext(Dispatchers.Default) {
                    val image = Bitmap.createBitmap(192, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
                    ByteArrayOutputStream().use { out -> image.compress(Bitmap.CompressFormat.JPEG, 90, out); image.recycle(); out.toByteArray() }
                }
                val result = test.analyze("Name the dominant background color in this image. Reply with only RED, BLUE, GREEN or UNKNOWN.", jpeg)
                check(result.content.trim().trim('"', '.', ' ').equals("RED", ignoreCase = true)) { "Cloudflare replied, but the image test did not pass. Try connecting again." }
                withContext(Dispatchers.IO) { store.save(credentials) }
                existing = credentials; token = ""
                say("Connected. Image test: ${result.elapsedMs / 1000.0} seconds. Return to Home and say describe the view.")
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { say(CloudflareVisionErrors.message(error)) }
            finally { test.close(); engine = null; busy = false }
        }
    }

    private fun open(url: String) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    override fun onPause() { engine?.close(); job?.cancel(); speaker?.stop(); busy = false; super.onPause() }
    override fun onDestroy() { speaker?.shutdown(); super.onDestroy() }
    companion object {
        private const val INSTRUCTIONS = "https://developers.cloudflare.com/workers-ai/get-started/rest-api/"
        private const val LICENSE = "https://github.com/meta-llama/llama-models/blob/main/models/llama3_2/LICENSE"
        private const val USE_POLICY = "https://github.com/meta-llama/llama-models/blob/main/models/llama3_2/USE_POLICY.md"
    }
}

object CloudflareVisionErrors {
    fun message(error: Exception): String = when (error) {
        is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> "Cloudflare took too long to answer. Check internet or use local mode."
        is java.io.IOException -> "Could not reach Cloudflare. Check your internet connection."
        is kotlinx.serialization.SerializationException -> "Cloudflare's answer could not be verified. Try again."
        else -> LocalAiPolicy.failureMessage(error)
    }
}
