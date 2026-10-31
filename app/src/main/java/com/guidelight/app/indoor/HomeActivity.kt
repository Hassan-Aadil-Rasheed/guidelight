package com.guidelight.app.indoor

import android.os.Bundle
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.ar.core.*
import com.guidelight.app.domain.*
import com.guidelight.app.ui.GuideLightTheme
import com.guidelight.app.ui.VoiceKeyboard
import com.guidelight.app.remote.RemoteBridge
import com.guidelight.app.remote.BridgePhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

enum class HomeMode { READY, RECORDING, WALKING }
data class HomeUiState(
    val map: HomeMap = HomeMap(), val loaded: Boolean = false, val storageError: Boolean = false,
    val cameraRunning: Boolean = false, val cameraReady: Boolean = false, val tracked: Boolean = false,
    val trackingText: String = "Scan your home marker", val mode: HomeMode = HomeMode.READY,
    val instruction: String = "Keep the marker fixed at your entrance.", val saving: Boolean = false,
    val destination: String = "", val remaining: Float? = null,
    val direction: GuidanceDirection = GuidanceDirection.STRAIGHT,
    val objectScanReady: Boolean = false, val warning: String? = null,
    val speechProblem: String? = null
)

class HomeActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var ui by mutableStateOf(HomeUiState())
    private lateinit var voice: HomeVoiceInput
    private var speaker: TextToSpeech? = null
    private var speechReady = false
    private var speechInitializing = false
    internal val speechStartedCount = AtomicInteger()
    internal val speechCompletedCount = AtomicInteger()
    internal val cameraFrameCount = AtomicInteger()
    internal var markerDatabaseSize = 0
        private set
    private var lastCameraTimestamp = 0L
    private var lastCameraHealthAt = 0L
    private val deferredSpeech = DeferredSpeech()
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var focusHeld = false
    private var speechSequence = 0L
    private var lastSpeechId = ""
    private val speechFocus by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAttributes).setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> { focusHeld = true; flushSpeech() }
                    // A notification permits continued playback. Repeatedly restarting
                    // spoken directions for CAN_DUCK can prevent any response finishing.
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Unit
                    else -> {
                        focusHeld = false
                        speaker?.stop()
                        if (!dictating()) deferredSpeech.defer()
                    }
                }
            }.build()
    }
    private val speech = GuidanceSpeech(
        isDictating = ::dictating,
        stopPlayback = { speaker?.stop(); releaseSpeechFocus(); deferredSpeech.clear() },
        output = { text, append ->
            if (deferredSpeech.allow(speechReady, lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))) {
                if (!focusHeld) focusHeld = audioManager.requestAudioFocus(speechFocus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                if (focusHeld) {
                    lastSpeechId = "home-${++speechSequence}"
                    val result = speaker?.speak(text, if (append) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH,
                        Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }, lastSpeechId)
                    if (result != TextToSpeech.SUCCESS) speechFailed()
                } else {
                    deferredSpeech.defer()
                    ui = ui.copy(speechProblem = "Voice is waiting for phone audio to become available.")
                }
            } else if (!speechReady && !speechInitializing) initializeSpeech()
        }
    )
    private var anchor: Anchor? = null
    private var position: HomePoint? = null
    private var lastPoseAt = 0L
    private var lastFrameAt = 0L
    private var recorder: HomeRecorder? = null
    private var follower: HomeFollower? = null
    private var lastInstruction = ""
    private var lastInstructionAt = 0L
    private var lastWarningAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        initializeSpeech()
        voice = HomeVoiceInput(this, available = { !ui.saving },
            silence = speech::silence, vibrate = ::vibrate, onCommand = ::command)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    flushSpeech()
                    delay(500)
                    if (ui.tracked && SystemClock.elapsedRealtime() - lastPoseAt > 1500L) resetTracking(true)
                }
            }
        }
        lifecycleScope.launch {
            try {
                val saved = applicationContext.homeMapStore.data.first()
                ui = ui.copy(map = saved, loaded = true)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                ui = ui.copy(loaded = true, storageError = true,
                    instruction = "Saved home data could not be read. It was kept unchanged. Room recording is unavailable.")
            }
        }
        setContent {
            GuideLightTheme {
                HomeScreen(ui, voice.state, onBack = { finish() }, onStart = ::startCamera,
                    onStopCamera = ::stopCamera, onSpeak = voice::arm, onText = voice::changeText,
                    onSubmit = voice::submit,
                    onChoose = ::navigateTo, onList = ::listRooms, onRepeat = ::repeatInstruction,
                    onEnd = ::endWalk,
                    camera = { modifier -> HomeCamera(this, modifier, ui.mode == HomeMode.WALKING, ui.direction,
                        ::updateTracking, { resetTracking(false) }, {
                            resetTracking(false)
                            ui = ui.copy(cameraRunning = false, instruction = "Camera unavailable. Check permission and Google Play Services for AR, then try again.")
                            speech.say(ui.instruction)
                        }, ::obstacle, ::objectAvailability) }
                )
                if (voice.state.armed && !ui.cameraRunning) HomeVoiceDialog(this, voice.state.request,
                    voice::changeText, voice::submit, voice::handleRemoteKey, { voice.cancel() }, hideKeyboard = voice.state.hideKeyboard)
            }
        }
    }

    private fun startCamera() {
        resetTracking(false)
        ui = ui.copy(cameraRunning = true, cameraReady = false, trackingText = "Scan your home marker",
            instruction = "Point at the whole Earth picture in good light.")
        speech.say(ui.instruction)
    }

    private fun stopCamera() {
        voice.cancel()
        resetTracking(false)
        ui = ui.copy(cameraRunning = false, cameraReady = false, instruction = "Camera stopped. Saved rooms are kept.")
        speech.say(ui.instruction)
    }

    private fun resetTracking(announce: Boolean) {
        val active = anchor != null || ui.mode != HomeMode.READY
        anchor?.detach(); anchor = null
        position = null; lastPoseAt = 0L; lastFrameAt = 0L
        recorder = null; follower = null
        ui = ui.copy(tracked = false, mode = HomeMode.READY, remaining = null, destination = "",
            direction = GuidanceDirection.STRAIGHT, trackingText = "Scan your home marker again",
            instruction = if (active) "Tracking stopped. Saved rooms are kept. Scan the marker again." else ui.instruction)
        if (announce && active) { vibrate(400); speech.say(ui.instruction) }
    }

    private fun updateTracking(session: Session, frame: Frame) {
        var justAligned = false
        if (!ui.cameraRunning) return
        if (frame.timestamp != lastCameraTimestamp) {
            lastCameraTimestamp = frame.timestamp
            cameraFrameCount.incrementAndGet()
        }
        val healthAt = SystemClock.elapsedRealtime()
        if (healthAt - lastCameraHealthAt > 2000) {
            lastCameraHealthAt = healthAt
            markerDatabaseSize = session.config.augmentedImageDatabase.numImages
            if (com.guidelight.app.BuildConfig.DEBUG) android.util.Log.d("GuideHomeCamera",
                "camera=${session.cameraConfig.cameraId} state=${frame.camera.trackingState} reason=${frame.camera.trackingFailureReason} markers=$markerDatabaseSize frames=${cameraFrameCount.get()}")
        }
        if (!ui.cameraReady) ui = ui.copy(cameraReady = true)
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            if (anchor != null) resetTracking(true)
            val text = when (frame.camera.trackingFailureReason) {
                TrackingFailureReason.INSUFFICIENT_LIGHT -> "More light is needed. Keep the Earth picture visible."
                TrackingFailureReason.EXCESSIVE_MOTION -> "Move the phone slowly and keep the Earth picture visible."
                TrackingFailureReason.INSUFFICIENT_FEATURES -> "Keep the whole Earth picture and its surroundings in view."
                TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera paused. Close other camera apps, then retry."
                else -> "Hold the phone steady with the Earth picture in view."
            }
            if (ui.trackingText != text) ui = ui.copy(trackingText = text)
            return
        }
        if (anchor == null) {
            if (!ui.loaded) return
            // Reacquisition must also consider an image already tracked in this session;
            // its update can have occurred while camera tracking or the activity was paused.
            val images = session.getAllTrackables(AugmentedImage::class.java)
                .filter { it.name in HomeMarkers.supported && it.trackingState != TrackingState.STOPPED }
            val tracked = images.filter { it.trackingState == TrackingState.TRACKING &&
                it.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING }
            val image = if (ui.map.nodes.isEmpty()) tracked.firstOrNull()
                else tracked.firstOrNull { it.name == ui.map.marker }
            if (image == null) {
                val hint = if (tracked.isNotEmpty() && ui.map.nodes.isNotEmpty())
                    "Use the same Earth picture that you used to save your rooms."
                    else "Hold the phone steady with the whole Earth picture visible."
                if (images.isNotEmpty() && ui.trackingText != hint) ui = ui.copy(trackingText = hint)
                return
            }
            val found = image.createAnchor(image.centerPose)
            if (found.trackingState != TrackingState.TRACKING || HomeCoordinates.point(found.pose, frame.camera.pose) == null) {
                found.detach()
                ui = ui.copy(trackingText = "Fix the marker upright on the wall.")
                return
            }
            anchor = found
            justAligned = true
            ui = ui.copy(map = if (ui.map.nodes.isEmpty()) ui.map.copy(marker = image.name) else ui.map,
                tracked = true, trackingText = "Home aligned", instruction =
                "Home ready. Say “This is my kitchen” to save a room, or “List my rooms” to hear them.")
        }
        val reference = anchor ?: return
        if (reference.trackingState != TrackingState.TRACKING) { resetTracking(true); return }
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAt < 250L) return
        lastFrameAt = now
        val point = HomeCoordinates.point(reference.pose, frame.camera.pose)
        if (point == null || position?.let { point.distance(it) > 1f } == true) { resetTracking(true); return }
        position = point; lastPoseAt = now
        if (justAligned) {
            // Learn the walked path from the known reference automatically. Naming a room
            // commits it; users need not switch recording modes.
            if (ui.loaded && !ui.storageError) {
                recorder = try { HomeRecorder(ui.map, point) } catch (_: IllegalArgumentException) { null }
                ui = ui.copy(mode = if (recorder != null) HomeMode.RECORDING else HomeMode.READY)
                if (recorder == null && ui.map.rooms.isEmpty()) ui = ui.copy(instruction = "Move closer to the home picture and scan it again before adding rooms.")
            }
            speech.say("Marker found. ${ui.instruction}")
        }
        try {
            recorder?.observe(point)
        } catch (error: IllegalArgumentException) {
            recorder = null
            ui = ui.copy(mode = HomeMode.READY, instruction = error.message ?: "Recording stopped.")
            speech.say(ui.instruction)
        }
        follower?.let { navigation ->
            val forward = HomeCoordinates.forward(reference.pose, frame.camera.pose)
            val progress = navigation.update(point, forward.first, forward.second)
            ui = ui.copy(instruction = progress.instruction, remaining = progress.remaining,
                direction = when (progress.turn) { -1 -> GuidanceDirection.LEFT; 1 -> GuidanceDirection.RIGHT; else -> GuidanceDirection.STRAIGHT })
            if (progress.offPath || progress.arrived) {
                follower = null
                ui = ui.copy(mode = HomeMode.READY, remaining = null, destination = "", direction = GuidanceDirection.STRAIGHT)
                vibrate(if (progress.offPath) 400 else 160)
                speech.say(progress.instruction)
            } else if ((progress.instruction != lastInstruction && now - lastInstructionAt >= 2500L || now - lastInstructionAt >= 12_000L)
                && now - lastWarningAt > 3000L && !voice.state.armed) {
                lastInstruction = progress.instruction; lastInstructionAt = now
                speech.say("${progress.instruction} About ${distance(progress.remaining)} remaining.")
            }
        }
    }

    private fun freshPosition(): HomePoint? {
        if (!ui.tracked || SystemClock.elapsedRealtime() - lastPoseAt > 1000L) {
            ui = ui.copy(instruction = "Scan the fixed home marker first.")
            speech.say(ui.instruction)
            return null
        }
        return position
    }

    private fun startRecording() {
        if (!ui.loaded || ui.storageError || ui.saving) { speech.say("Room storage is unavailable. Try again when loading finishes."); return }
        if (ui.mode == HomeMode.WALKING) { speech.say("End the walk before recording."); return }
        if (recorder != null) { speech.say("Recording is already on. Say this is my kitchen to save a room."); return }
        val point = freshPosition() ?: return
        try {
            recorder = HomeRecorder(ui.map, point)
            ui = ui.copy(mode = HomeMode.RECORDING, instruction = "Home ready. Walk with a helper. Stop in each room and say “This is my kitchen” or its name.")
            speech.say(ui.instruction)
        } catch (error: IllegalArgumentException) { showMessage(error.message ?: "Recording could not start.") }
    }

    private fun saveRoom(name: String) {
        if (ui.saving) { speech.say("Saving a room. Please wait."); return }
        val point = freshPosition() ?: return
        val recording = recorder ?: run { showMessage("Scan the home picture at your starting spot, then walk with a helper and name the room."); return }
        val snapshot = try { recording.saveRoom(name, point) } catch (error: IllegalArgumentException) {
            showMessage(error.message ?: "Room could not be saved."); return
        }
        ui = ui.copy(saving = true)
        lifecycleScope.launch {
            try {
                val saved = applicationContext.homeMapStore.updateData { snapshot }
                ui = ui.copy(map = saved, saving = false,
                    instruction = if (ui.tracked) "$name saved. Name another room, or say “Take me to my $name”." else ui.instruction)
                if (ui.tracked) speech.say(ui.instruction)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                recorder = null
                ui = ui.copy(saving = false, mode = HomeMode.READY)
                showMessage("Room could not be saved. Previously saved rooms are kept. Recording stopped.")
            }
        }
    }

    private fun finishRecording() {
        if (ui.saving) { speech.say("Saving a room. Please wait."); return }
        if (ui.mode != HomeMode.RECORDING) { showMessage("Recording is not active."); return }
        recorder = null
        if (ui.mode == HomeMode.RECORDING) ui = ui.copy(mode = HomeMode.READY)
        showMessage("Recording finished. ${ui.map.rooms.size} rooms saved. Say list rooms to hear them.")
    }

    private fun navigateTo(room: HomeRoom) {
        val point = freshPosition() ?: return
        if (!ui.objectScanReady) { showMessage("Object scanning is not ready. Wait, then choose the room again with your helper."); return }
        try {
            val route = HomeRouting.plan(recorder?.snapshot() ?: ui.map, point, room)
            recorder = null
            follower = HomeFollower(route)
            lastInstruction = ""; lastInstructionAt = 0L
            ui = ui.copy(mode = HomeMode.WALKING, destination = room.name,
                remaining = route.points.zipWithNext().sumOf { (a, b) -> a.distance(b).toDouble() }.toFloat(),
                instruction = "Starting the recorded path to ${room.name}.")
            speech.say(ui.instruction)
        } catch (error: IllegalArgumentException) { showMessage(error.message ?: "No recorded path was found.") }
    }

    private fun endWalk() {
        follower = null; recorder = null
        ui = ui.copy(mode = HomeMode.READY)
        ui = ui.copy(destination = "", remaining = null, direction = GuidanceDirection.STRAIGHT)
        showMessage("Walk ended.")
    }

    private fun listRooms() {
        if (ui.map.rooms.isEmpty()) { showMessage("No rooms saved yet. Scan the home picture, then stop in a room and say “This is my kitchen”."); return }
        ui = ui.copy(instruction = "Saved rooms: ${ui.map.rooms.joinToString { it.name }}.")
        speech.sequence(listOf("${ui.map.rooms.size} saved rooms.") + ui.map.rooms.mapIndexed { index, room ->
            "Room ${index + 1}. ${room.name}."
        } + "Say take me to a room name, or choose room one.")
    }

    private fun repeatInstruction() {
        speech.sequence(listOfNotNull(ui.warning, ui.instruction,
            ui.remaining?.let { "About ${distance(it)} remaining." }))
    }

    private fun command(text: String) {
        VoiceKeyboard.hide(this)
        if (HomeAppVoiceCommands.handle(this, text, voice, ::showMessage, AppVoiceCommands.legacyHelp,
                { listOf(ui.trackingText, ui.instruction).joinToString(". ") })) return
        when (HomeToolCommands.resolve(text)) {
            HomeToolAction.START_CAMERA -> { if (ui.loaded) startCamera() else showMessage("Home data is loading. Try scan home marker again shortly."); return }
            HomeToolAction.STOP_CAMERA -> { stopCamera(); return }
            HomeToolAction.LEGACY_RECORDINGS -> { showMessage("Previous room recordings are already open. ${AppVoiceCommands.legacyHelp}"); return }
            HomeToolAction.LOCAL_AI_SETUP, HomeToolAction.MORE_OPTIONS, HomeToolAction.FEWER_OPTIONS -> {
                showMessage("Say Home navigation to use the camera assistant options."); return
            }
            null -> Unit
        }
        when (val result = HomeCommands.parse(text, ui.map.rooms)) {
            is HomeCommand.SaveRoom -> saveRoom(result.name)
            is HomeCommand.GoTo -> ui.map.rooms.firstOrNull { it.name == result.name }?.let(::navigateTo)
                ?: showMessage("${result.name} is not saved. Say list rooms to hear saved places.")
            is HomeCommand.Choose -> ui.map.rooms.getOrNull(result.index)?.let(::navigateTo)
            HomeCommand.StartRecording -> startRecording()
            HomeCommand.FinishRecording -> finishRecording()
            HomeCommand.ListRooms -> listRooms()
            HomeCommand.Repeat -> repeatInstruction()
            HomeCommand.EndWalk -> endWalk()
            HomeCommand.Unknown -> showMessage("To save a room, say “This is my kitchen”. To hear saved rooms, say “List my rooms”.")
        }
    }

    private fun showMessage(message: String) { ui = ui.copy(instruction = message); speech.say(message) }

    private fun obstacle(alert: ObstacleAlert) {
        // A laptop used for alignment is expected in front of the camera.
        // Repeating object warnings here can cut off the setup/command response.
        if (!ui.tracked) { if (ui.warning != null) ui = ui.copy(warning = null); return }
        val warning = when (alert.risk) {
            ObstacleRisk.NONE -> null
            ObstacleRisk.STOP -> "Stop. Possible obstacle ahead."
            ObstacleRisk.CAUTION -> when (alert.position) {
                ObstaclePosition.LEFT -> "Caution. Object on your left."
                ObstaclePosition.RIGHT -> "Caution. Object on your right."
                else -> "Caution. Object ahead."
            }
        }
        if (ui.warning != warning) ui = ui.copy(warning = warning)
        val now = SystemClock.elapsedRealtime()
        if (warning != null && now - lastWarningAt >= 4500L) {
            lastWarningAt = now
            vibrate(if (alert.risk == ObstacleRisk.STOP) 350 else 150)
            if (ui.mode == HomeMode.WALKING) speech.say(warning)
        }
    }

    private fun objectAvailability(ready: Boolean) {
        if (ui.objectScanReady && !ready && ui.mode == HomeMode.WALKING) {
            follower = null
            ui = ui.copy(mode = HomeMode.READY, destination = "", remaining = null, direction = GuidanceDirection.STRAIGHT)
            showMessage("Stop. Object scanning is unavailable. Check with your helper, then choose the room again.")
        }
        if (ui.objectScanReady != ready) ui = ui.copy(objectScanReady = ready)
    }

    private fun vibrate(duration: Long) {
        getSystemService(Vibrator::class.java)?.let {
            if (it.hasVibrator()) it.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
    private fun dictating(): Boolean = (::voice.isInitialized && voice.state.armed) ||
        RemoteBridge.status.value.phase in listOf(BridgePhase.PREPARING, BridgePhase.HOLDING, BridgePhase.SUBMITTING)

    private fun releaseSpeechFocus() {
        if (focusHeld) audioManager.abandonAudioFocusRequest(speechFocus)
        focusHeld = false
    }

    private fun flushSpeech() {
        if (deferredSpeech.take(speechReady, lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED), dictating()))
            repeatInstruction()
    }

    private fun speechFailed() {
        releaseSpeechFocus()
        ui = ui.copy(speechProblem = "Voice output failed. Try the read-aloud button again.")
    }

    private fun initializeSpeech() {
        speechInitializing = true; speechReady = false; lastSpeechId = ""
        speaker?.shutdown()
        speaker = TextToSpeech(this, this).also { engine ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { speechStartedCount.incrementAndGet() }
                override fun onDone(id: String?) {
                    speechCompletedCount.incrementAndGet()
                    runOnUiThread {
                        if (id == lastSpeechId) { releaseSpeechFocus(); ui = ui.copy(speechProblem = null) }
                    }
                }
                override fun onError(id: String?) { runOnUiThread { if (id == lastSpeechId) speechFailed() } }
            })
        }
    }

    override fun onInit(result: Int) {
        speechInitializing = false
        if (result == TextToSpeech.SUCCESS) {
            speaker?.setAudioAttributes(speechAttributes)
            speechReady = speaker?.setLanguage(Locale.US) in
                listOf(TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE)
            // If initialization took longer than the first command, read the current response.
            // The shared speech gate still drops it while Wispr is recording.
            if (speechReady) {
                ui = ui.copy(speechProblem = null)
                if (ui.cameraRunning) { deferredSpeech.defer(); flushSpeech() }
            }
        }
        if (!speechReady) ui = ui.copy(speechProblem = "English voice is unavailable. Check the phone's text-to-speech settings.")
    }
    override fun onPause() {
        voice.cancel(); speech.silence(); resetTracking(false)
        if (ui.cameraRunning) deferredSpeech.defer()
        super.onPause()
    }
    override fun onDestroy() { anchor?.detach(); releaseSpeechFocus(); speaker?.shutdown(); super.onDestroy() }
}

fun distance(metres: Float): String = String.format(Locale.US, "%.1f metres", metres)
