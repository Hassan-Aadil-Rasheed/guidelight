package com.guidelight.app.indoor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.Keep
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.guidelight.app.domain.*
import com.guidelight.app.ui.VoiceKeyboard
import com.guidelight.app.remote.BridgePhase
import com.guidelight.app.remote.RemoteBridge
import com.guidelight.app.ui.GuideLightTheme
import com.introlab.rtabmap.RTABMapLib as Native
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import com.guidelight.app.vision.*
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

enum class SavedHomeStage { READY, PREPARING, RECORDING, FINDING, RECOGNIZED, SAVING, CLOSING, ERROR }
data class SavedHomeState(val stage: SavedHomeStage = SavedHomeStage.READY,
    val message: String = "Map your home once with a helper. Then test recognition after reopening.",
    val hasMap: Boolean = false, val preview: Boolean = false, val views: Int = 0, val rooms: List<String> = emptyList(),
    val cameraStatus: String = "Starting camera…", val aiReady: Boolean = false,
    val optionsRequest: Int = 0, val optionsExpanded: Boolean = false, val aiBusy: Boolean = false, val aiMessage: String = "Local AI is not installed", val aiAnswer: String = "",val cloudReady: Boolean=false)

/** Qualification stage: genuine persistent visual mapping, with no walking authorization. */
@Keep
class SavedHomeActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var ui by mutableStateOf(SavedHomeState())
    private val worker = Executors.newSingleThreadExecutor()
    private val pointer = AtomicLong()
    private var surfaceReady = CountDownLatch(1)
    private var surface: GLSurfaceView? = null
    @Volatile private var sessionClosed = true
    private var sessionGeneration = 0
    private var draft: File? = null
    private var localizing = false
    private var workingCopy: File? = null
    @Volatile private var cameraFeed: ArCoreMapFeed? = null
    @Volatile private var cameraWidth = 0
    @Volatile private var cameraHeight = 0
    private var startupAt = 0L
    private var autoFindOnResume = false
    private val observations = mutableListOf<SavedRoomObservation>()
    private var knownRooms = emptyMap<Int, String>()
    private lateinit var maps: SavedHomeStore
    private lateinit var voice: HomeVoiceInput
    private lateinit var localAi: LocalVisionEngine
    private var cloudCredentials: CloudflareCredentials?=null
    private var cloudEngine: CloudflareVisionEngine?=null
    private var aiJob: Job? = null
    private var aiGeneration=0
    internal var lastAiReasoned=false
        private set
    internal var lastAiElapsedMs=0L
        private set
    private var speaker: TextToSpeech? = null
    private var speechReady = false
    private val recognition = SavedMapRecognition()
    internal val renderedFrames = AtomicInteger()
    internal val nativeStats = AtomicInteger()
    internal val recordedNodeCount = AtomicInteger()
    private var announcedRoom: String? = null
    internal var lastInliers = 0
        private set
    internal var lastMatchSummary = "No visual match received"
        private set
    private val speech = GuidanceSpeech(
        isDictating = { (::voice.isInitialized && voice.state.armed) || RemoteBridge.status.value.phase in
            listOf(BridgePhase.PREPARING, BridgePhase.HOLDING, BridgePhase.SUBMITTING) },
        stopPlayback = { speaker?.stop(); Unit },
        output = { message, append -> if (speechReady) speaker?.speak(message,
            if (append) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, null, "saved-home-${System.nanoTime()}") }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // The cache override is internal-only and used by on-device tests; this Activity is not exported.
        val fixture = intent.getStringExtra("isolatedTestDirectory")?.takeIf { com.guidelight.app.BuildConfig.DEBUG && it.matches(Regex("map-test-[a-f0-9-]{36}")) }
        maps = SavedHomeStore(if (fixture == null) File(filesDir, "saved-home") else File(cacheDir, fixture))
        localAi=LocalVisionEngine(this,LocalAiProfile.FAST)
        cloudCredentials=CloudflareConfigStore(this).read()
        ui = ui.copy(hasMap = maps.current() != null, aiReady=localAi.installed||cloudCredentials!=null,cloudReady=cloudCredentials!=null,
            aiMessage=if(cloudCredentials!=null)"Cloudflare is ready" else if(localAi.installed) "Fast vision is ready" else "Set up fast vision to describe camera views")
        autoFindOnResume = ui.hasMap
        voice = HomeVoiceInput(this, available = { ui.stage !in busyStages },
            silence = speech::silence, vibrate = ::vibrate, onCommand = ::command, feedback = ::say)
        speaker = TextToSpeech(this, this)
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(500)
                if (ui.stage in listOf(SavedHomeStage.PREPARING, SavedHomeStage.RECORDING) && !sessionClosed &&
                    startupAt != 0L && SystemClock.elapsedRealtime() - startupAt > 25_000 && (cameraFeed?.frames?.get() ?: 0) == 0) {
                    closeSession("The camera has not recorded a usable view. Point it at a well-lit room and retry.", true)
                }
                cameraFeed?.let { if (ui.cameraStatus != it.trackingMessage) ui = ui.copy(cameraStatus = it.trackingMessage) }
                if (cameraFeed?.tracked == false) recognition.reset()
                if (ui.stage == SavedHomeStage.RECOGNIZED && !recognition.recognized(SystemClock.elapsedRealtime())) {
                    ui = ui.copy(stage = SavedHomeStage.FINDING, message = "Looking for a saved place. Slowly look around the room.")
                }
            }
        } }
        setContent { GuideLightTheme { SavedHomeScreen(ui, voice.state,
            onBack = { finish() }, onCreateMap = { startSession(false) }, onFind = ::locateMe,
            onSave = ::saveMap, onStop = { closeSession("Test stopped. Your saved map is kept.") },
            onSpeak = voice::arm, onText = voice::changeText, onSubmit = voice::submit,
            onAi=::askAi, onAiSetup=::prepareAi, onAiCancel=::cancelAi,onCloudSetup=::openCloudSetup,
            onLegacy = { startActivity(Intent(this, HomeActivity::class.java)) },
            camera = { modifier -> MapCamera(modifier) })

        } }
    }

    private fun say(message: String) { ui = ui.copy(message = message); speech.say(message) }
    private fun vibrate(duration: Long) {
        getSystemService(Vibrator::class.java)?.let {
            if (it.hasVibrator()) it.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
    private fun command(text: String) {
        VoiceKeyboard.hide(this)
        if (LocalAiCommands.isCloudSetup(text)) { openCloudSetup(); return }
        if (HomeAppVoiceCommands.handle(this, text, voice, ::say, AppVoiceCommands.homeHelp,
                { listOf(ui.cameraStatus, ui.aiAnswer.ifBlank { ui.aiMessage }, ui.message).filter { it.isNotBlank() }.joinToString(". ") })) return
        HomeToolCommands.resolve(text)?.let { action ->
            when (action) {
                HomeToolAction.LOCAL_AI_SETUP -> prepareAi()
                HomeToolAction.LEGACY_RECORDINGS -> if (ui.preview) say("Say end test before opening previous room recordings.") else startActivity(Intent(this, HomeActivity::class.java))
                HomeToolAction.MORE_OPTIONS, HomeToolAction.FEWER_OPTIONS -> {
                    val expanded = action == HomeToolAction.MORE_OPTIONS
                    ui = ui.copy(optionsRequest = ui.optionsRequest + 1, optionsExpanded = expanded)
                    say(if (expanded) "More options. Say Cloudflare settings, explain my status, detailed analysis, or fewer options."
                        else "Extra options closed. Say describe view or identify room.")
                }
                HomeToolAction.START_CAMERA -> if (ui.preview) say(ui.cameraStatus) else locateMe()
                HomeToolAction.STOP_CAMERA -> { cancelAi(); closeSession("Camera stopped. Your saved map is kept.") }
            }
            return
        }
        if(LocalAiCommands.isCloudSetup(text)){openCloudSetup();return}
        if(LocalAiCommands.isCancel(text)) {cancelAi();say("AI analysis canceled.");return}
        LocalAiCommands.parse(text)?.let {askAi(it);return}
        SavedHomeControls.parse(text)?.let { control ->
            when (control) {
                SavedHomeControl.RECORD -> {cancelAi();startSession(false)}
                SavedHomeControl.SAVE -> {cancelAi();saveMap()}
                SavedHomeControl.LOCATE -> locateMe()
                SavedHomeControl.REPEAT -> speech.say(ui.message)
                SavedHomeControl.STOP -> {cancelAi();closeSession("Test stopped. Your saved map is kept.")}
            }
            return
        }
        val homeCommand = HomeCommands.parse(text, emptyList())
        if (homeCommand is HomeCommand.SaveRoom) {
            if (ui.stage != SavedHomeStage.RECORDING || ui.views == 0) { say("Start recording your home before naming a room."); return }
            observations += SavedRoomObservation(homeCommand.name, System.currentTimeMillis() / 1000.0)
            ui = ui.copy(rooms = observations.map { it.room }.distinct())
            say("${homeCommand.name} marked. Look around each room and name it, then save the home map.")
            return
        }
        if (homeCommand == HomeCommand.ListRooms) {
            val names = if (ui.stage == SavedHomeStage.RECORDING) ui.rooms else knownRooms.values.distinct()
            say(if (names.isEmpty()) "No rooms are marked yet." else "Marked rooms: ${names.joinToString()}.")
            return
        }
        say("Say what can I say to hear all Home commands. You can say find my position, create home map, save map, list my rooms, describe view, or back to navigation. Room directions are not active yet.")
    }

    private fun currentVerifiedRoom(): String? {
        if(cameraFeed?.tracked!=true || cameraFeed?.inferencePaused==true || !localizing || sessionClosed)return null
        return knownRooms[recognition.verifiedNode(SystemClock.elapsedRealtime())]
    }
    private fun statusKey(): String = when {
        ui.stage==SavedHomeStage.RECORDING -> "recording"
        currentVerifiedRoom()!=null -> "localized"
        !ui.hasMap -> "no_map"
        cameraFeed?.tracked!=true -> "tracking_unavailable"
        else -> "not_localized"
    }
    private fun cancelAi() {
        ++aiGeneration;aiJob?.cancel();aiJob=null
        cloudEngine?.close();cloudEngine=null
        if(::localAi.isInitialized)localAi.close()
        cameraFeed?.inferencePaused=false
        recognition.reset()
        ui=ui.copy(aiBusy=false,aiMessage=if(cloudCredentials!=null)"Cloudflare is ready" else if(::localAi.isInitialized && localAi.installed) "${localAi.profile.label} is ready" else "Set up camera assistance")
    }
    private fun openCloudSetup(){cancelAi();startActivity(Intent(this,CloudflareSetupActivity::class.java))}
    private fun prepareAi() {
        if(aiJob?.isActive==true)return
        val generation=++aiGeneration
        ui=ui.copy(aiBusy=true,aiAnswer="")
        aiJob=lifecycleScope.launch {
            try {
                localAi.prepare {message -> runOnUiThread {if(generation==aiGeneration)ui=ui.copy(aiMessage=message)} }
                if(generation==aiGeneration) {ui=ui.copy(aiBusy=false,aiReady=true,aiMessage="Local AI is ready");say("Local AI is ready. Say describe the view or identify this room.")}
            } catch(cancel: CancellationException) {throw cancel}
            catch(error: Exception) { if(generation==aiGeneration){localAi.close();ui=ui.copy(aiBusy=false,aiReady=localAi.installed,aiMessage=LocalAiPolicy.failureMessage(error));say(ui.aiMessage)} }
        }
    }
    private fun askAi(task: LocalAiTask) {
        if(aiJob?.isActive==true){say("The AI is still thinking. Say cancel analysis to stop it.");return}
        if(ui.stage==SavedHomeStage.RECORDING){say("Save your home map before using local AI.");return}
        if(task!=LocalAiTask.EXPLAIN_STATUS && cameraFeed==null){say("Open find my position or create home map to start the Home camera first.");return}
        val remote=cloudCredentials?.takeIf {task!=LocalAiTask.DETAILED_VIEW}?.let {CloudflareVisionEngine(it)}
        cloudEngine=remote
        val profile=if(task in listOf(LocalAiTask.DETAILED_VIEW,LocalAiTask.EXPLAIN_STATUS))LocalAiProfile.DETAILED else LocalAiProfile.FAST
        if(localAi.profile!=profile) {localAi.close();localAi=LocalVisionEngine(this,profile)}
        if(remote!=null)localAi.close()
        val generation=++aiGeneration
        ui=ui.copy(aiBusy=true,aiAnswer="",aiMessage=if(remote!=null)"Preparing Cloudflare…" else "Preparing local AI…")
        lastAiReasoned=false;lastAiElapsedMs=0
        val analysisFeed=if(task==LocalAiTask.EXPLAIN_STATUS||remote!=null)null else cameraFeed
        analysisFeed?.inferencePaused=true
        if(analysisFeed!=null)recognition.reset()
        say(if(remote!=null && task==LocalAiTask.IDENTIFY_ROOM)"Keep the phone still while I compare your saved room pictures." else if(remote!=null)"Sending this request to Cloudflare." else when(task) {
            LocalAiTask.EXPLAIN_STATUS -> "Detailed reasoning will examine the current app status. This can take a while."
            LocalAiTask.DETAILED_VIEW -> "Detailed reasoning can take a while. Keep the phone still."
            else -> "Keep the phone still. Reading the camera view."
        })
        aiJob=lifecycleScope.launch {
            fun progress(message: String) {runOnUiThread {if(generation==aiGeneration)ui=ui.copy(aiMessage=message)}}
            try {
                if(remote==null)localAi.prepare(::progress)
                val answer: String
                val result: LocalAiResult
                if(task==LocalAiTask.EXPLAIN_STATUS) {
                    val key=statusKey()
                    val facts=kotlinx.serialization.json.buildJsonObject {
                        put("status",kotlinx.serialization.json.JsonPrimitive(key))
                        put("verified_room",currentVerifiedRoom()?.let {kotlinx.serialization.json.JsonPrimitive(it)} ?: kotlinx.serialization.json.JsonNull)
                        put("camera_status",kotlinx.serialization.json.JsonPrimitive(ui.cameraStatus))
                        put("saved_map_available",kotlinx.serialization.json.JsonPrimitive(ui.hasMap))
                        put("walking_route_available",kotlinx.serialization.json.JsonPrimitive(false))
                    }
                    result=if(remote!=null)remote.analyze("Explain these verified facts: $facts",system=LocalAiPrompts.statusSystem)
                        else localAi.reason(LocalAiPrompts.statusSystem,"Explain these verified facts: $facts",emptyList(),::progress)
                    if(generation==aiGeneration){lastAiElapsedMs=result.elapsedMs;lastAiReasoned=result.usedReasoning}
                    check(statusKey()==key) {"Your status changed while the AI was thinking. Ask where am I for the current status."}
                    answer=LocalAiPolicy.statusAnswer(result.content,key,currentVerifiedRoom())
                } else if(task==LocalAiTask.IDENTIFY_ROOM && remote!=null) {
                    val photos=withContext(Dispatchers.IO){maps.referencePhotos()}
                    check(photos.isNotEmpty()){ "No usable saved room pictures are available. Record and save your rooms first." }
                    val capture=cameraFeed?.snapshot() ?: error("The Home camera has paused.")
                    val batches=withContext(Dispatchers.Default) {
                        val image=capture.jpeg(512)
                        listOf(CloudflareImage.roomComparison(image,photos.map{it.reference to it.jpeg}))
                    }
                    check(SystemClock.elapsedRealtime()-capture.capturedAt<=3_000){"The camera image is too old. Try again."}
                    progress("Comparing saved room views…")
                    val match=remote.identifyRoom(batches){done,total ->progress("Comparing saved room views · $done of $total")}
                    check(match.complete){RoomMatchPolicy.describe(match,photos.map {it.reference},null)}
                    check(SystemClock.elapsedRealtime()-capture.capturedAt<=35_000){"The room view is no longer current. Keep the phone still and try again."}
                    result=LocalAiResult("",false,match.elapsedMs)
                    answer=RoomMatchPolicy.describe(match,photos.map {it.reference},currentVerifiedRoom())
                } else {
                    val sheet=if(task==LocalAiTask.IDENTIFY_ROOM) withContext(Dispatchers.IO) {maps.referenceSheet()} else null
                    if(task==LocalAiTask.IDENTIFY_ROOM) check(sheet!=null) {"No usable saved room pictures are available yet. Record and save your rooms first."}
                    val capture=cameraFeed?.snapshot() ?: error("The Home camera has paused.")
                    val image=withContext(Dispatchers.Default) {capture.jpeg(if(remote!=null)512 else 256)}
                    check(SystemClock.elapsedRealtime()-capture.capturedAt<=3_000) {"The camera image is too old. Try again."}
                    val images=if(sheet==null)listOf(image) else listOf(image,sheet.jpeg)
                    val question=if(sheet==null) "Describe the visible scene and objects in the first image. No room references are supplied; reference_match must be null."
                        else "Compare the first image with the second image's references: ${sheet.rooms.joinToString {it.id+" = "+it.name}}. If distinctive features do not match, use null."
                    val prompt=if(profile.thinking)LocalAiPrompts.sceneSystem else LocalAiPrompts.sceneSystem.replace("Think briefly, then output", "Output")
                    result=if(remote!=null) {
                        progress("Waiting for Cloudflare…")
                        val upload=if(sheet==null)image else withContext(Dispatchers.Default){CloudflareImage.withReferences(image,sheet.jpeg)}
                        val cloudQuestion=if(sheet==null)question else "Compare CURRENT VIEW at the top with SAVED REFERENCES below: ${sheet.rooms.joinToString {it.id+" = "+it.name}}. Match only distinctive details, otherwise use null."
                        val cloudPrompt=LocalAiPrompts.sceneSystem.replace("Think briefly, then output","Output")
                            .replace("Inspect image 1.","Inspect the supplied image.")
                            .replace("Image 2, if supplied, contains saved views labeled R1 etc.","For a comparison image, inspect CURRENT VIEW at the top and saved views labeled R1 etc below.")
                        remote.analyzeScene(cloudQuestion,upload,system="You are a JSON-only visual classifier. Return one valid JSON object, without explanation or markdown.\n"+cloudPrompt,
                            references=sheet?.rooms.orEmpty(),onFormatting={progress("Finishing the description…")}).also {
                            check(SystemClock.elapsedRealtime()-capture.capturedAt<=15_000){"That camera image is no longer current. Keep the phone still and try again."}
                        }
                    } else localAi.reason(prompt,question,images,::progress,LocalAiSchemas.scene(sheet?.rooms.orEmpty()))
                    if(generation==aiGeneration){lastAiElapsedMs=result.elapsedMs;lastAiReasoned=result.usedReasoning}
                    answer="From the captured image: "+LocalAiPolicy.describe(LocalAiPolicy.parseScene(result.content),sheet?.rooms.orEmpty(),currentVerifiedRoom())
                }
                if(generation==aiGeneration) {
                    lastAiReasoned=result.usedReasoning;lastAiElapsedMs=result.elapsedMs
                    ui=ui.copy(aiBusy=false,aiReady=true,aiMessage="${if(remote!=null)"Cloudflare" else "Analysis"} complete · ${result.elapsedMs/1000} seconds",aiAnswer=answer)
                    say(answer)
                }
            } catch(timeout: TimeoutCancellationException) {
                if(generation==aiGeneration) {
                    localAi.close();val message="The camera could not capture a fresh image. Keep Home open and retry."
                    ui=ui.copy(aiBusy=false,aiMessage=message);say(message)
                }
            } catch(cancel: CancellationException) {throw cancel}
            catch(error: Exception) {if(generation==aiGeneration) {
                localAi.close();val message=if(remote!=null)CloudflareVisionErrors.message(error) else LocalAiPolicy.failureMessage(error)
                ui=ui.copy(aiBusy=false,aiReady=localAi.installed||cloudCredentials!=null,aiMessage=message);say(message)
            } } finally {
                remote?.close()
                if(generation==aiGeneration) {
                    analysisFeed?.inferencePaused=false
                    if(analysisFeed!=null)recognition.reset()
                }
            }
        }
    }

    private fun locateMe() {
        if (localizing && !sessionClosed && pointer.get() != 0L) {
            val now = SystemClock.elapsedRealtime()
            val tracked = cameraFeed?.tracked == true
            if (!tracked) recognition.reset()
            val room=knownRooms[recognition.verifiedNode(now)]
            if(room==null && (localAi.installed||cloudCredentials!=null) && !ui.aiBusy && tracked) askAi(LocalAiTask.IDENTIFY_ROOM)
            else say(SavedHomeControls.locationReply(tracked, recognition.recognized(now),room,
                cameraFeed?.trackingMessage ?: "The camera is starting."))
        } else if (ui.stage == SavedHomeStage.RECORDING) {
            say("You are recording a home map. Say save map first, then say where am I.")
        } else if (ui.stage in busyStages) {
            say("The home map is opening or saving. Please wait, then ask where am I again.")
        } else startSession(true)
    }

    private fun startSession(find: Boolean) {
        if (pointer.get() != 0L || ui.stage in busyStages) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            say("Allow camera access in GuideLight settings first."); return
        }
        val saved = maps.current()
        if (find && saved == null) { say("Record and save a home map first."); return }
        val operation = ++sessionGeneration
        autoFindOnResume = false
        sessionClosed = false; localizing = find; recognition.reset(); startupAt = SystemClock.elapsedRealtime()
        knownRooms = if (find && saved != null) maps.rooms(saved).associate { it.node to it.room } else emptyMap()
        observations.clear()
        surfaceReady = CountDownLatch(1); renderedFrames.set(0); nativeStats.set(0); recordedNodeCount.set(0); lastInliers = 0; announcedRoom = null
        val recording = if (find) null else maps.newRecording()
        draft = recording
        ui = ui.copy(stage = SavedHomeStage.PREPARING, views = 0, rooms = knownRooms.values.distinct(),
            message = if (find) "Opening your saved home map…" else "Starting the home camera…")
        worker.execute {
            try {
                if (sessionClosed || operation != sessionGeneration) return@execute
                val application = Native.createNativeApplication(this)
                pointer.set(application)
                check(application != 0L && Native.isBuiltWith(application, 1)) { "ARCore mapping is unavailable on this device." }
                if (sessionClosed || operation != sessionGeneration) return@execute
                runOnUiThread { if (!sessionClosed && operation == sessionGeneration) ui = ui.copy(preview = true) }
                check(surfaceReady.await(8, TimeUnit.SECONDS)) { "The mapping camera did not start." }
                if (sessionClosed) return@execute
                Native.setAppendMode(application, false)
                Native.setLocalizationMode(application, find)
                // A new session can begin in any mapped room, not just at shutdown's pose.
                Native.setMappingParameter(application, "Mem/InitWMWithAllNodes", find.toString())
                Native.setMappingParameter(application, "RGBD/StartAtOrigin", "true")
                Native.setMappingParameter(application, "Rtabmap/DetectionRate", "1")
                Native.setMappingParameter(application, "Rtabmap/LoopThr", "0.2")
                Native.setMappingParameter(application, "Vis/MinInliers", "30")
                Native.setMappingParameter(application, "Mem/RehearsalSimilarity", "1.0")
                Native.setMappingParameter(application, "RGBD/ProximityBySpace", "false")
                val database = if (find) File(cacheDir, "recognize-${java.util.UUID.randomUUID()}.db").also {
                    saved!!.copyTo(it); workingCopy = it
                } else recording!!
                val result = Native.openDatabase(application, database.path, false, false, false)
                check(result >= 0) { "The saved home map could not be opened. It was kept unchanged." }
                Native.setCamera(application, 0)
                Native.setMapCloudShown(application, true); Native.setOdomCloudShown(application, true)
                Native.setGraphVisible(application, true); Native.setGridVisible(application, false)
                if (sessionClosed) return@execute
                check(Native.startCamera(application, null, applicationContext, this, 3)) { "The mapping camera is unavailable. Close other camera apps and retry." }
                cameraFeed = ArCoreMapFeed(applicationContext).also { it.resume() }
                if (sessionClosed) return@execute
                Native.setPausedMapping(application, false)
                runOnUiThread {
                    if (!sessionClosed && operation == sessionGeneration) {
                        ui = ui.copy(stage = if (find) SavedHomeStage.FINDING else SavedHomeStage.RECORDING)
                        say(if (find) "Looking for a saved place. Slowly look around the room."
                            else "Point the camera at the room and move slowly sideways. When views appear, name each room with Wispr. Say save map when finished.")
                    }
                }
            } catch (error: Throwable) {
                runOnUiThread { if (operation == sessionGeneration) closeSession(error.message ?: "Saved-home mapping could not start.", true) }
            }
        }
    }

    private fun saveMap() {
        if (ui.stage != SavedHomeStage.RECORDING || pointer.get() == 0L) return
        if (ui.views < 10) { say("Record more views first. Move slowly through the rooms with your helper."); return }
        if (ui.rooms.size < 3) { say("For this first test, name three connected rooms before saving the map."); return }
        val application = pointer.get(); val recording = draft ?: return
        ui = ui.copy(stage = SavedHomeStage.SAVING)
        say("Saving your home map. Keep GuideLight open.")
        worker.execute {
            try {
                Native.setPausedMapping(application, true)
                // Save elsewhere: the native engine reopens its output file. Keep its
                // database in place until the engine is closed before committing.
                Native.save(application, recording.path)
                maps.validate(recording)
                runOnUiThread { closeSession("Home map saved. Close the app, move to a different mapped room with your helper, reopen Home, and choose find my position.", commit = recording) }
            } catch (error: Exception) {
                runOnUiThread { closeSession(error.message ?: "The map could not be saved. Your previous saved map is kept.", true) }
            }
        }
    }

    private fun closeSession(message: String, error: Boolean = false, commit: File? = null) {
        if (sessionClosed && pointer.get() == 0L) { if (!isDestroyed) say(message); return }
        sessionClosed = true; ++sessionGeneration; recognition.reset()
        voice.cancel(); speech.silence()
        surface?.onPause()
        val application = pointer.getAndSet(0L)
        ui = ui.copy(stage = SavedHomeStage.CLOSING, preview = false)
        worker.execute {
            var finalMessage = message; var failed = error
            try {
                val closingApplication = if (application != 0L) application else pointer.getAndSet(0L)
                cameraFeed?.close(); cameraFeed = null
                if (closingApplication != 0L) { Native.stopCamera(closingApplication); Native.destroyNativeApplication(closingApplication) }
                workingCopy?.let { it.delete(); workingCopy = null }
                if (commit != null) maps.commit(commit, observations.toList())
            } catch (failure: Throwable) { finalMessage = failure.message ?: "The map was kept, but could not be finalized."; failed = true }
            runOnUiThread {
                if (!isDestroyed) {
                    knownRooms = maps.rooms().associate { it.node to it.room }
                    ui = ui.copy(stage = if (failed) SavedHomeStage.ERROR else SavedHomeStage.READY, hasMap = maps.current() != null)
                    say(finalMessage)
                    if (autoFindOnResume && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && ui.hasMap) startSession(true)
                }
            }
        }
    }

    @Composable private fun MapCamera(modifier: Modifier) {
        AndroidView(modifier = modifier, factory = { context ->
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(3)
                setEGLConfigChooser(8,8,8,8,24,0)
                preserveEGLContextOnPause = true
                setRenderer(object : GLSurfaceView.Renderer {
                    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                        val application = pointer.get()
                        if (application != 0L) {
                            Native.initGlContent(application)
                            val camera = getSystemService(CameraManager::class.java)
                            val orientation = camera.getCameraCharacteristics("0").get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                            Native.setScreenRotation(application, display?.rotation ?: 0, orientation)
                            surfaceReady.countDown()
                        }
                    }
                    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                        cameraWidth = width; cameraHeight = height
                        pointer.get().takeIf { it != 0L }?.let { Native.setupGraphic(it, width, height) }
                    }
                    override fun onDrawFrame(gl: GL10?) {
                        pointer.get().takeIf { it != 0L }?.let {
                            try {
                                cameraFeed?.update(it,cameraWidth,cameraHeight,display?.rotation ?: 0)
                                Native.render(it); renderedFrames.incrementAndGet()
                                cameraFeed?.drawPreview(cameraWidth,cameraHeight)
                            } catch (failure: Exception) {
                                runOnUiThread { if (!sessionClosed) closeSession("Camera tracking could not continue. Please retry.", true) }
                            }
                        }
                    }
                })
                surface = this
            }
        }, onRelease = { it.onPause(); if (surface === it) surface = null })
    }

    // Exact upstream JNI callback contracts; no image or feature descriptors are logged.
    @Keep fun cameraEventCallback(type: Int, key: String?, value: String?) {
        if (type < 0) runOnUiThread { recognition.reset() }
    }
    @Keep fun rtabmapInitEventCallback(status: Int, info: String?) { /* Native openDatabase returns its final result. */ }
    @Keep fun updateProgressionCallback(count: Int, maximum: Int) { /* Saving remains modal until native completion. */ }
    @Keep fun updateStatsCallback(nodes: Int, words: Int, points: Int, polygons: Int, update: Float,
        loop: Int, hypothesisNode: Int, memory: Int, inliers: Int, matches: Int, features: Int,
        hypothesis: Float, clouds: Int, fps: Float, rejected: Int, rehearsal: Float,
        optimizationError: Float, optimizationRatio: Float, distance: Float, fastMovement: Int,
        landmark: Int, x: Float, y: Float, z: Float, roll: Float, pitch: Float, yaw: Float) {
        nativeStats.incrementAndGet()
        recordedNodeCount.set(nodes)
        runOnUiThread {
            if (sessionClosed || isDestroyed) return@runOnUiThread
            lastInliers = inliers
            lastMatchSummary = "accepted=$loop candidate=$hypothesisNode inliers=$inliers matches=$matches hypothesis=$hypothesis rejected=$rejected features=$features"
            if (ui.views != nodes) ui = ui.copy(views = nodes)
            if (localizing && cameraFeed?.inferencePaused!=true && ui.stage in listOf(SavedHomeStage.FINDING, SavedHomeStage.RECOGNIZED)) {
                val found = recognition.observe(PlaceMatch(loop, inliers, matches, rejected, hypothesis, x,y,z), SystemClock.elapsedRealtime())
                val room = knownRooms[recognition.verifiedNode(SystemClock.elapsedRealtime())]
                if (found && (ui.stage != SavedHomeStage.RECOGNIZED || room != null && room != announcedRoom)) {
                    ui = ui.copy(stage = SavedHomeStage.RECOGNIZED)
                    if (room != null) announcedRoom = room
                    say(if (room != null) "Your location matches the saved $room view."
                        else "Saved home recognized. The room has not been identified yet. Slowly look around.")
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            speaker?.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            speechReady = speaker?.setLanguage(Locale.US) in listOf(0,1,2)
            if (speechReady) speech.say(ui.message)
        }
    }
    override fun onResume() {
        super.onResume()
        if(::localAi.isInitialized){
            cloudCredentials=CloudflareConfigStore(this).read()
            ui=ui.copy(cloudReady=cloudCredentials!=null,aiReady=localAi.installed||cloudCredentials!=null,
                aiMessage=if(cloudCredentials!=null)"Cloudflare is ready" else if(localAi.installed)"${localAi.profile.label} is ready" else "Set up camera assistance")
        }
        if (autoFindOnResume && maps.current() != null && ui.stage !in busyStages) startSession(true)
    }
    override fun onPause() {
        cancelAi()
        autoFindOnResume = maps.current() != null
        if (!sessionClosed || pointer.get() != 0L) closeSession("Camera paused. Your saved home is kept. Choose find my position to recognize it again.")
        voice.cancel(); speech.silence()
        super.onPause()
    }
    override fun onDestroy() { if(::localAi.isInitialized)localAi.close();speaker?.shutdown(); worker.shutdown(); super.onDestroy() }
    companion object { private val busyStages = setOf(SavedHomeStage.PREPARING, SavedHomeStage.SAVING, SavedHomeStage.CLOSING) }
}

