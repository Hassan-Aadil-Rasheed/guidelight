package com.guidelight.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.view.KeyEvent
import android.view.WindowManager
import android.view.Window
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.guidelight.app.domain.*
import com.guidelight.app.navigation.*
import com.guidelight.app.ui.*
import com.guidelight.app.vision.CameraObstacleMonitor
import com.guidelight.app.vision.PathGuidanceOverlay
import com.guidelight.app.vision.OutdoorRouteCamera
import com.guidelight.app.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener, InputManager.InputDeviceListener {
    private var ui by mutableStateOf(GuideUiState())
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private val cameraSpeechPriority = CameraSpeechPriority()
    private var cameraMessage = false
    private var buttonMessage = false
    private val buttonConnectionCue = ButtonConnectionCue()
    private var pendingNavigation = false
    private var pendingScanUntil = 0L
    private var pendingWarning: Pair<ObstacleAlert, Long>? = null
    private var lastCameraMessage = ""
    private val guidanceSpeech = GuidanceSpeech(
        isDictating = { ui.voiceArmed },
        stopPlayback = { textToSpeech?.stop(); Unit },
        output = { message, append ->
            if (ttsReady) {
                val id = "${if (cameraMessage) "camera" else if (buttonMessage) "button" else "guidelight"}-${System.nanoTime()}"
                if (cameraMessage) cameraSpeechPriority.started(id, android.os.SystemClock.elapsedRealtime())
                val result = textToSpeech?.speak(message, if (append) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, null, id)
                if (result == TextToSpeech.ERROR) cameraSpeechPriority.finished(id)
            }
        }
    )
    private var cameraMonitor: CameraObstacleMonitor? = null
    private var pathOverlay: PathGuidanceOverlay? = null
    private val placeSearch = PlaceSearchService()
    private val routing = PedestrianRoutingService()
    private lateinit var locationTracker: WalkingLocationTracker
    private var lastLocation: Location? = null
    private var pendingQuery: String? = null
    private var navigationSession: NavigationSession? = null
    private lateinit var cameraHeading: CameraHeading
    private var networkJob: Job? = null
    private var voiceJob: Job? = null
    private var manualVoiceTimeout: Job? = null
    private var locationTimeout: Job? = null
    private var lastSpoken = "GuideLight ready. Choose a destination or open Camera to scan ahead."
    private data class CameraBinding(val monitor: CameraObstacleMonitor, val overlay: PathGuidanceOverlay)
    private val remoteGate = RemoteHoldGate()
    private var remoteSessionDevice: Int? = null
    private var remoteStartJob: Job? = null
    private var voiceDestinationBeforeArming = ""
    private var destinationScreenReady = true
    private val objectAnnouncements = ObjectAnnouncementGate()
    private var sceneAt = 0L
    private var lastRiskAt = 0L

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ui = ui.copy(cameraAllowed = hasPermission(Manifest.permission.CAMERA),
            preciseLocationAllowed = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
        if (hasLocationPermission()) startLocationUpdates()
        else if (pendingQuery != null) showError("Allow location permission in phone settings to find nearby places.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ui.copy(destination = savedInstanceState?.getString("destination").orEmpty(),
            remoteEnabled = getSharedPreferences("remote", MODE_PRIVATE).getBoolean("enabled", false))
        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val originalWindowCallback = window.callback
        window.callback = object : Window.Callback by originalWindowCallback {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                handleRemoteKeyEvent(event) || originalWindowCallback.dispatchKeyEvent(event)
        }
        locationTracker = WalkingLocationTracker(this, ::onLocationChanged)
        cameraHeading = CameraHeading(this)
        textToSpeech = TextToSpeech(this, this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    if (sceneAt != 0L && android.os.SystemClock.elapsedRealtime() - sceneAt > 2_000) {
                        ui = ui.copy(sceneSummary = null, primaryObjectDescription = null)
                    }
                    if (lastRiskAt != 0L && ui.alertVisible && android.os.SystemClock.elapsedRealtime() - lastRiskAt > 8_000)
                        ui = ui.copy(alertVisible = false)
                    refreshNavigation(); drainCameraSpeech(); drainButtonConnectionSpeech(); delay(500)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
            RemoteBridge.status.collect { bridge ->
                val previousPhase = ui.remote.phase
                val disconnectedDuringHold = ui.remote.connected && !bridge.connected && ui.remote.phase in listOf(BridgePhase.PREPARING, BridgePhase.HOLDING)
                ui = ui.copy(remote = bridge)
                if (bridge.phase == BridgePhase.SUBMITTING && ui.voiceArmed) {
                    ui = ui.copy(voiceKeyboardHidden = true)
                    VoiceKeyboard.hide(this@MainActivity)
                }
                if (bridge.phase in listOf(BridgePhase.IDLE, BridgePhase.ERROR, BridgePhase.SUBMITTING)) remoteSessionDevice = null
                if (bridge.phase != previousPhase && bridge.phase in listOf(BridgePhase.HOLDING, BridgePhase.SUBMITTING)) {
                    val vibrator = getSystemService(Vibrator::class.java)
                    if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createOneShot(
                        if (bridge.phase == BridgePhase.HOLDING) 60 else 120, VibrationEffect.DEFAULT_AMPLITUDE))
                }
                if (disconnectedDuringHold) {
                    voiceJob?.cancel()
                    ui = ui.copy(voiceArmed = false, error = "Button control disconnected. Check Wispr Flow.")
                } else if (bridge.phase == BridgePhase.ERROR) {
                    voiceJob?.cancel()
                    ui = ui.copy(voiceArmed = false, error = bridge.message)
                    announce(bridge.message, interrupt = true)
                } else if (bridge.phase == BridgePhase.SUBMITTING && !ui.voiceArmed) {
                    RemoteBridge.cancelSubmissionWait()
                } else if (bridge.phase == BridgePhase.SUBMITTING && ui.voiceArmed && currentVoiceText().isNotBlank()) {
                    scheduleVoiceCommand()
                }
            }
            }
        }
        setContent {
            GuideLightTheme {
                GuideLightApp(ui, GuideActions(
                    onIndoorTest = ::openHomeNavigation,
                    onDestination = ::onDestinationChanged, onSpeak = ::armWisprInput,
                    onSearch = ::searchDestination, onChoosePlace = ::requestWalkingRoute,
                    onDismissPlaces = ::dismissPlaces,
                    onChoiceInput = ::onChoiceInputChanged, onSubmitChoice = ::submitChoice,
                    onRepeatPlaces = ::repeatGuidance,
                    onRepeat = ::repeatGuidance,
                    onWalkCommand = ::onWalkCommandChanged, onSubmitWalkCommand = ::submitWalkCommand,
                    onWalkAction = ::performWalkAction,
                    onStopConfirmation = ::setStopConfirmation,
                    onStop = ::stopNavigation, onHelp = {
                        ui = if (ui.routeActive) ui.copy(walkingPanel = if (it) WalkingPanel.HELP else WalkingPanel.CAMERA)
                        else ui.copy(helpVisible = it)
                    },
                    onCameraPermission = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
                    onLocationSettings = {
                        val intent = if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                        else Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        startActivity(intent)
                    },
                    onRemoteSetup = { ui = ui.copy(helpVisible = false, remoteSetupVisible = it) },
                    onRemoteEnabled = ::setRemoteEnabled,
                    onRemotePermission = ::openRemotePermission,
                    onHardwareKey = ::handleRemoteKeyEvent,
                    onDestinationScreenReady = {
                        destinationScreenReady = it
                        if (!it && ui.voiceArmed) {
                            voiceJob?.cancel(); manualVoiceTimeout?.cancel(); RemoteBridge.release()
                            ui = ui.copy(voiceArmed = false)
                        }
                    }
                )) { modifier -> CameraFeed(modifier) }
                // App-owned modal dialogs still need a normal editor for Wispr.
                if (ui.voiceArmed && (ui.helpVisible || ui.remoteSetupVisible || ui.busy)) {
                    com.guidelight.app.indoor.HomeVoiceDialog(this, ui.voiceRequest,
                        onText = ::onDictationText, onSubmit = ::submitDictation,
                        onKey = ::handleRemoteKeyEvent, onCancel = ::cancelVoiceInput,
                        hideKeyboard = ui.voiceKeyboardHidden)
                }
            }
        }
        val missing = buildList {
            if (!hasPermission(Manifest.permission.CAMERA)) add(Manifest.permission.CAMERA)
            if (!hasLocationPermission()) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        ui = ui.copy(cameraAllowed = hasPermission(Manifest.permission.CAMERA),
            preciseLocationAllowed = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
        applyVoiceNavigationIntent(intent)
    }

    @Composable
    private fun CameraFeed(modifier: Modifier) {
        if (ui.routeActive) {
            OutdoorRouteCamera(this, modifier, ui.relativeRouteBearing, ui.groundPreview, ui.direction,
                beforeCreate = { cameraMonitor?.stop(); cameraMonitor = null; pathOverlay = null },
                onStatus = { ready, status ->
                    if (ui.cameraReady != ready || ui.cameraStatus != status) ui = ui.copy(cameraReady = ready, cameraStatus = status)
                }, onObjects = ::onObjectsDetected, onRisk = ::warnAboutObstacle)
            return
        }
        AndroidView(
            modifier = modifier,
            factory = { context ->
                FrameLayout(context).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    val preview = PreviewView(context).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val overlay = PathGuidanceOverlay(context)
                    addView(preview, FrameLayout.LayoutParams(-1, -1))
                    addView(overlay, FrameLayout.LayoutParams(-1, -1))
                    pathOverlay = overlay
                    overlay.setRoute(ui.routeActive, ui.direction)
                    val monitor = CameraObstacleMonitor(
                        context, this@MainActivity, preview,
                        onReady = { ui = ui.copy(cameraReady = true, cameraStatus = "Camera scanning") },
                        onDetections = { boxes -> overlay.setDetections(boxes); onObjectsDetected(boxes) }, onWarning = ::warnAboutObstacle,
                        onError = { ui = ui.copy(cameraReady = false, cameraStatus = "Camera unavailable. Check permission and try again.") }
                    ).also { it.start() }
                    cameraMonitor = monitor
                    tag = CameraBinding(monitor, overlay)
                }
            },
            onRelease = { view ->
                val owned = view.tag as? CameraBinding
                owned?.monitor?.stop()
                if (cameraMonitor === owned?.monitor) {
                    cameraMonitor = null
                    pathOverlay = null
                    ui = ui.copy(cameraReady = false, cameraStatus = "Starting camera…")
                }
            },
            update = { (it.tag as? CameraBinding)?.overlay?.setRoute(ui.routeActive, ui.direction) }
        )
    }

    private fun onDestinationChanged(value: String) {
        val previous = ui.destination
        ui = ui.copy(destination = value, error = null, status = "")
        hideKeyboardForTranscript(value, previous)
        voiceJob?.cancel()
        if (!ui.routeActive && ui.places == null && ui.voiceArmed && value.isNotBlank() && ui.remote.phase !in listOf(BridgePhase.PREPARING, BridgePhase.HOLDING)) scheduleVoiceCommand()
    }

    private fun onChoiceInputChanged(value: String) {
        val previous = ui.choiceInput
        ui = ui.copy(choiceInput = value, error = null)
        hideKeyboardForTranscript(value, previous)
        voiceJob?.cancel()
        if (ui.places != null && ui.voiceArmed && value.isNotBlank() && ui.remote.phase !in listOf(BridgePhase.PREPARING, BridgePhase.HOLDING)) scheduleVoiceCommand()
    }

    private fun onWalkCommandChanged(value: String) {
        val previous = ui.walkCommandInput
        ui = ui.copy(walkCommandInput = value, error = null)
        hideKeyboardForTranscript(value, previous)
        voiceJob?.cancel()
        if ((ui.routeActive || !destinationScreenReady) && ui.voiceArmed && value.isNotBlank() && ui.remote.phase !in listOf(BridgePhase.PREPARING, BridgePhase.HOLDING)) scheduleVoiceCommand()
    }

    private fun currentVoiceText() = when {
        ui.routeActive || !destinationScreenReady -> ui.walkCommandInput
        ui.places != null -> ui.choiceInput
        else -> ui.destination
    }

    private fun scheduleVoiceCommand() {
        voiceJob?.cancel()
        val choices = ui.places
        val walk = navigationSession
        val wasWalking = ui.routeActive
        val wasCamera = !destinationScreenReady
        val wasConfirming = ui.confirmEndWalk
        val text = currentVoiceText()
        voiceJob = lifecycleScope.launch {
            delay(1_200)
            if (!ui.voiceArmed || ui.places !== choices || currentVoiceText() != text ||
                ui.routeActive != wasWalking || (!destinationScreenReady) != wasCamera || navigationSession !== walk || ui.confirmEndWalk != wasConfirming) return@launch
            RemoteBridge.transcriptReceived()
            if (wasWalking || wasCamera) submitWalkCommand() else if (choices != null) submitChoice() else searchDestination()
        }
    }

    private fun repeatGuidance() {
        if (ui.voiceArmed) {
            voiceJob?.cancel()
            RemoteBridge.release()
            RemoteBridge.cancelSubmissionWait()
            ui = ui.copy(voiceArmed = false)
        }
        refreshNavigation(speak = false)
        if (ui.places != null) readSuggestions()
        else announce(listOfNotNull(ui.lastWarning, ui.instruction.takeIf(String::isNotBlank))
            .joinToString(" ").ifBlank { lastSpoken }, interrupt = true)
    }

    private fun submitWalkCommand() {
        if (!ui.routeActive && destinationScreenReady) return
        voiceJob?.cancel()
        ui = ui.copy(voiceArmed = false)
        RemoteBridge.transcriptReceived()
        val text = ui.walkCommandInput
        if (handleAppCommand(text)) return
        val action = WalkVoiceCommands.resolve(text, ui.confirmEndWalk)
        ui = ui.copy(walkCommandInput = "")
        Log.i("GuideWalk", "voice action=$action")
        if (!ui.routeActive && VoicePhrases.normalize(text) in setOf("navigation", "back to navigation", "open navigation", "choose destination", "find a place", "new destination", "stop scanning", "stop camera", "close camera")) {
            requestVoiceScreen("navigate"); announce("Speak a destination or say nearest followed by a place type.", interrupt = true); return
        }
        if (!ui.routeActive && action == WalkVoiceAction.UNKNOWN) {
            val destination = DestinationParser.parse(text)
            if (destination != null && (NearbyRequest.fromQuery(text) != null || destination != VoicePhrases.politeText(text))) {
                requestVoiceScreen("navigate")
                ui = ui.copy(destination = destination)
                findPlaces(destination); return
            }
        }
        performWalkAction(action)
    }

    private fun performWalkAction(action: WalkVoiceAction) {
        if (!ui.routeActive) {
            if (destinationScreenReady) return
            when (action) {
                WalkVoiceAction.SCAN, WalkVoiceAction.REPEAT, WalkVoiceAction.SHOW_CAMERA -> scanView()
                WalkVoiceAction.REPEAT_ALERT -> announceCamera(ui.lastWarning?.let { "Last alert. $it" } ?: "No object alert to repeat.")
                WalkVoiceAction.CAMERA_STATUS -> announceCamera(if (ui.cameraReady) "Object scanning is active." else "Object scanning is not ready. ${ui.cameraStatus}.")
                WalkVoiceAction.LOCATION -> announceCamera(if (ui.locationReady) "Phone location is ready. Say nearest followed by a place type to find places." else "Finding your phone location.")
                WalkVoiceAction.END, WalkVoiceAction.CLOSE_CONTROLS -> {
                    requestVoiceScreen("navigate"); announce("Camera closed. Speak a destination or say nearest followed by a place type.", interrupt = true)
                }
                WalkVoiceAction.HELP -> announceCamera("Say scan surroundings, repeat alert, camera status, or back to navigation. Use the GuideLight button or microphone, then Wispr Flow.")
                else -> showError("Say scan surroundings, repeat alert, camera status, or back to navigation.")
            }
            return
        }
        when (action) {
            WalkVoiceAction.REPEAT -> repeatGuidance()
            WalkVoiceAction.END -> stopNavigation()
            WalkVoiceAction.KEEP_WALKING -> setStopConfirmation(false)
            WalkVoiceAction.OPEN_CONTROLS -> {
                ui = ui.copy(walkingPanel = WalkingPanel.CONTROLS)
                announce("Walking controls. Say repeat directions, scan surroundings, show map, location status, help, or end walk.", interrupt = true)
            }
            WalkVoiceAction.CLOSE_CONTROLS, WalkVoiceAction.SHOW_CAMERA -> {
                ui = ui.copy(walkingPanel = WalkingPanel.CAMERA)
                announce("Camera view. ${ui.instruction}", interrupt = true)
            }
            WalkVoiceAction.SHOW_MAP -> {
                ui = ui.copy(walkingPanel = WalkingPanel.MAP)
                announce("Walking map. Say recenter, full route, or camera view.", interrupt = true)
            }
            WalkVoiceAction.RECENTER, WalkVoiceAction.FULL_ROUTE -> {
                ui = ui.copy(walkingPanel = WalkingPanel.MAP, mapOverview = action == WalkVoiceAction.FULL_ROUTE,
                    mapCameraRequest = ui.mapCameraRequest + 1)
                announce(if (action == WalkVoiceAction.FULL_ROUTE) "Showing the full route." else "Map following your location.", interrupt = true)
            }
            WalkVoiceAction.LOCATION -> announce("${ui.walkingPosition?.label ?: "Finding your location"}. " +
                "${ui.remainingDistance.ifBlank { ui.routeDistance }} remaining. ${ui.routeDuration}.", interrupt = true)
            WalkVoiceAction.CAMERA_STATUS -> announce(if (ui.cameraReady)
                "Object scanning active. ${ui.cameraStatus}." else "Object scanning is not ready. ${ui.cameraStatus}.", interrupt = true)
            WalkVoiceAction.SCAN -> scanView()
            WalkVoiceAction.REPEAT_ALERT -> announceCamera(ui.lastWarning?.let { "Last alert. $it" } ?: "No object alert to repeat.")
            WalkVoiceAction.HELP -> {
                ui = ui.copy(walkingPanel = WalkingPanel.HELP)
                announce("Press the GuideLight button, speak with Wispr Flow, then press again to send. ${WalkVoiceCommands.help(false)} " +
                    "Object size means appearance in the camera view, not real size or distance.", interrupt = true)
            }
            WalkVoiceAction.UNKNOWN -> showError(if (ui.confirmEndWalk) WalkVoiceCommands.help(true)
                else "Command not recognised. Say scan surroundings, repeat directions, help, or end walk.")
        }
    }

    private fun scanView() {
        val fresh = ui.cameraReady && sceneAt != 0L && android.os.SystemClock.elapsedRealtime() - sceneAt <= 2_000
        if (fresh) announceCamera(ui.sceneSummary ?: "Scanning the view. Try again shortly.")
        else {
            pendingScanUntil = android.os.SystemClock.elapsedRealtime() + 5_000
            announceCamera("Scanning the view. Keep the rear camera facing ahead.")
        }
    }

    private fun requestVoiceScreen(screen: String) {
        voiceJob?.cancel(); manualVoiceTimeout?.cancel(); RemoteBridge.release()
        pendingScanUntil = 0L; pendingWarning = null; sceneAt = 0L
        objectAnnouncements.reset(); cameraSpeechPriority.clear()
        ui = ui.copy(voiceArmed = false, walkCommandInput = "", destination = "", error = null,
            cameraReady = false, sceneSummary = null, primaryObjectDescription = null,
            screenRequest = ui.screenRequest + 1, requestedScreen = screen)
    }

    private fun setStopConfirmation(show: Boolean) {
        if (!ui.routeActive) return
        voiceJob?.cancel()
        RemoteBridge.release()
        ui = ui.copy(confirmEndWalk = show, voiceArmed = false, walkCommandInput = "", error = null)
        announce(if (show) "End this walk? ${WalkVoiceCommands.help(true)}"
            else "Keep walking. ${ui.instruction}", interrupt = true)
    }

    private fun readSuggestions() {
        val places = ui.places ?: return
        val nearbyIntro = (ui.nearbyLabel ?: ui.nearbyCategory?.plural)?.let { "Nearby $it. Suggestions ordered by approximate distance." }
        announceSequence(listOfNotNull(nearbyIntro) + PlaceChoiceVoice.spokenSuggestions(places, ui.remoteEnabled))
    }

    private fun submitChoice() {
        if (handleAppCommand(ui.choiceInput)) return
        VoiceKeyboard.hide(this)
        val places = ui.places ?: return
        voiceJob?.cancel()
        ui = ui.copy(voiceArmed = false)
        RemoteBridge.transcriptReceived()
        when (val action = PlaceChoiceVoice.resolve(ui.choiceInput, places)) {
            is PlaceChoiceAction.Choose -> requestWalkingRoute(action.place)
            PlaceChoiceAction.Repeat -> readSuggestions()
            PlaceChoiceAction.ChangeDestination -> dismissPlaces()
            is PlaceChoiceAction.Invalid -> showError(action.message)
        }
    }

    private fun dismissPlaces() {
        voiceJob?.cancel()
        RemoteBridge.release()
        ui = ui.copy(places = null, nearbyCategory = null, nearbyLabel = null, choiceInput = "", voiceArmed = false, error = null, status = "")
        announce("Speak a new destination with Wispr Flow.", interrupt = true)
    }

    private fun handleRemoteKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.action == KeyEvent.ACTION_UP && remoteGate.up(event.deviceId)) {
                if (event.isCanceled) {
                    ui = ui.copy(voiceArmed = false)
                    RemoteBridge.release()
                    remoteSessionDevice = null
                }
                // A physical UP ends this click, not dictation. The second click
                // releases our owned screen pointer, avoiding the remote's power-off hold.
                return true
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (remoteGate.down(event.deviceId, event.device?.name, event.repeatCount,
                    ui.remoteEnabled)) {
                    RemoteHoldGate.Result.REPEAT -> return true
                    RemoteHoldGate.Result.START -> {
                        Log.i("GuideRemote", "GuideLight button press received; walking=${ui.routeActive}")
                        if (RemoteBridge.status.value.phase in listOf(BridgePhase.PREPARING, BridgePhase.HOLDING)) {
                            RemoteBridge.release()
                        } else if (!ui.remote.connected && !RemoteBridge.isPermissionEnabled(this)) {
                            announce(RemoteBridge.unavailableMessage(this))
                            ui = ui.copy(remotePermissionGranted = false, remoteSetupVisible = true)
                        } else startRemoteInput(event.deviceId)
                        return true
                    }
                    RemoteHoldGate.Result.IGNORE -> Unit
                }
            }
        }
        return false
    }

    private fun startRemoteInput(deviceId: Int) {
        if (remoteStartJob?.isActive == true) {
            remoteStartJob?.cancel()
            remoteSessionDevice = null
            ui = ui.copy(voiceArmed = false)
            return
        }
        voiceJob?.cancel()
        prepareVoiceInput()
        remoteSessionDevice = deviceId
        // The saved permission is separate from a transport that is still reconnecting.
        // A press just after returning to GuideLight waits instead of reopening setup.
        remoteStartJob = lifecycleScope.launch {
            val ready = withTimeoutOrNull(3_000) { RemoteBridge.status.first { it.connected }; true } == true
            if (!ui.voiceArmed || remoteSessionDevice != deviceId || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            if (!ready || !RemoteBridge.begin()) {
                remoteSessionDevice = null
                val message = if (!ready) RemoteBridge.unavailableMessage(this@MainActivity) else "Button control is busy. Try again."
                ui = ui.copy(voiceArmed = false, error = message, remotePermissionGranted = RemoteBridge.isPermissionEnabled(this@MainActivity))
                announce(message, interrupt = true)
            }
        }
    }

    private fun openRemotePermission() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun refreshSelfieConnection(announceChange: Boolean = true) {
        val manager = getSystemService(InputManager::class.java)
        val connected = manager.inputDeviceIds.any { GuideLightButton.matches(manager.getInputDevice(it)?.name) }
        val changed = ui.selfieConnected != connected
        ui = ui.copy(selfieConnected = connected)
        if (changed && ui.remoteEnabled && (announceChange || connected)) buttonConnectionCue.changed(connected)
    }

    private fun drainButtonConnectionSpeech() {
        val safe = ttsReady && !cameraDictationActive() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            !cameraSpeechPriority.blocksNavigation(android.os.SystemClock.elapsedRealtime())
        buttonConnectionCue.take(ui.remoteEnabled, safe)?.let { announceButton(it, append = true) }
    }

    private fun announceButton(message: String, append: Boolean = false) {
        lastSpoken = message
        buttonMessage = true
        try { guidanceSpeech.say(message, append) } finally { buttonMessage = false }
    }

    override fun onInputDeviceAdded(deviceId: Int) = refreshSelfieConnection()
    override fun onInputDeviceChanged(deviceId: Int) = refreshSelfieConnection()
    override fun onInputDeviceRemoved(deviceId: Int) {
        refreshSelfieConnection()
        if (remoteGate.activeDevice == deviceId || remoteSessionDevice == deviceId) {
            remoteGate.cancel(); RemoteBridge.release()
            remoteSessionDevice = null
            ui = ui.copy(voiceArmed = false, error = "GuideLight button disconnected. Check Wispr Flow.")
        }
    }

    override fun onPause() {
        remoteStartJob?.cancel()
        val physicalPressActive = remoteGate.cancel()
        if (physicalPressActive || remoteSessionDevice != null || ui.voiceArmed) {
            Log.i("GuideRemote", "Voice input cancelled as GuideLight paused")
            voiceJob?.cancel()
            manualVoiceTimeout?.cancel()
            RemoteBridge.release()
            remoteSessionDevice = null
            ui = ui.copy(voiceArmed = false)
        }
        super.onPause()
    }

    private fun cancelVoiceInput() {
        voiceJob?.cancel(); manualVoiceTimeout?.cancel(); remoteStartJob?.cancel()
        RemoteBridge.release()
        ui = ui.copy(voiceArmed = false, voiceKeyboardHidden = true)
        VoiceKeyboard.hide(this)
    }

    private fun onDictationText(value: String) {
        when {
            ui.routeActive || !destinationScreenReady -> onWalkCommandChanged(value)
            ui.places != null -> onChoiceInputChanged(value)
            else -> onDestinationChanged(value)
        }
    }

    private fun submitDictation() {
        when {
            ui.routeActive || !destinationScreenReady -> submitWalkCommand()
            ui.places != null -> submitChoice()
            else -> searchDestination()
        }
    }

    private fun hideKeyboardForTranscript(value: String, previous: String) {
        if (ui.voiceArmed && value.isNotBlank() && value.length - previous.length > 1 &&
            ui.remote.phase !in listOf(BridgePhase.PREPARING, BridgePhase.HOLDING)) {
            ui = ui.copy(voiceKeyboardHidden = true)
            VoiceKeyboard.hide(this)
        }
    }

    private fun setRemoteEnabled(enabled: Boolean) {
        if (!enabled) {
            remoteGate.cancel(); RemoteBridge.release(); voiceJob?.cancel(); remoteStartJob?.cancel()
            remoteSessionDevice = null
            ui = ui.copy(voiceArmed = false)
        }
        getSharedPreferences("remote", MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
        ui = ui.copy(remoteEnabled = enabled, remotePermissionGranted = RemoteBridge.isPermissionEnabled(this))
        if (enabled) RemoteBridge.attach(this, this) else RemoteBridge.detach(this)
        updateScreenAwake()
    }

    private fun cancelPlaceSearch() {
        networkJob?.cancel(); locationTimeout?.cancel()
        pendingQuery = null; pendingNavigation = false
        ui = ui.copy(busy = false, status = "")
    }

    private fun openHomeNavigation() {
        if (ui.routeActive) {
            announce("End your outdoor walk first, then say Home navigation.", interrupt = true)
            return
        }
        cancelPlaceSearch()
        cancelVoiceInput()
        guidanceSpeech.silence()
        VoiceNavigation.home(this)
    }

    private fun handleAppCommand(text: String): Boolean {
        val action = AppVoiceCommands.resolve(text) ?: return false
        voiceJob?.cancel(); manualVoiceTimeout?.cancel()
        RemoteBridge.transcriptReceived()
        ui = ui.copy(voiceArmed = false, voiceKeyboardHidden = true, walkCommandInput = "", choiceInput = "",
            destination = if (!ui.routeActive && destinationScreenReady && ui.places == null) voiceDestinationBeforeArming else ui.destination)
        VoiceKeyboard.hide(this)
        when (action) {
            AppVoiceAction.HOME -> openHomeNavigation()
            AppVoiceAction.NAVIGATE -> if (ui.routeActive) announce("End your walk first to choose another destination.", interrupt = true)
                else { cancelPlaceSearch(); requestVoiceScreen("navigate"); announce("Navigation. Say a destination or Home navigation.", interrupt = true) }
            AppVoiceAction.CAMERA -> if (ui.routeActive) performWalkAction(WalkVoiceAction.SHOW_CAMERA)
                else { cancelPlaceSearch(); requestVoiceScreen("camera"); announce("Camera open. Say scan surroundings, or what can I say.", interrupt = true) }
            AppVoiceAction.HELP -> if (ui.routeActive) performWalkAction(WalkVoiceAction.HELP)
                else announce(AppVoiceCommands.navigationHelp, interrupt = true)
            AppVoiceAction.BACK, AppVoiceAction.CLOSE -> when {
                ui.helpVisible || ui.remoteSetupVisible -> {
                    ui = ui.copy(helpVisible = false, remoteSetupVisible = false)
                    announce("Closed. Say a destination, Home navigation, or what can I say.", interrupt = true)
                }
                ui.confirmEndWalk -> setStopConfirmation(false)
                ui.places != null -> dismissPlaces()
                ui.routeActive -> performWalkAction(WalkVoiceAction.CLOSE_CONTROLS)
                !destinationScreenReady -> { requestVoiceScreen("navigate"); announce("Back to navigation.", interrupt = true) }
                else -> announce("You are on Navigate. Say Home navigation, open camera, or a destination.", interrupt = true)
            }
            AppVoiceAction.SELFIE_SETUP -> {
                ui = ui.copy(helpVisible = false, remoteSetupVisible = true)
                announce("GuideLight button settings. Say enable GuideLight button, disable GuideLight button, Accessibility settings, or close settings.", interrupt = true)
            }
            AppVoiceAction.ENABLE_SELFIE, AppVoiceAction.DISABLE_SELFIE -> {
                val enabled = action == AppVoiceAction.ENABLE_SELFIE
                setRemoteEnabled(enabled)
                announce(if (!enabled) "GuideLight button disabled. Your choice is saved."
                    else if (ui.remotePermissionGranted) "GuideLight button enabled. Your choice is saved."
                    else "GuideLight button enabled in GuideLight. Android still needs button-control permission in Accessibility settings.", interrupt = true)
            }
            AppVoiceAction.CLEAR_DESTINATION, AppVoiceAction.CHANGE_DESTINATION -> if (ui.routeActive)
                announce("End your walk first to change destination.", interrupt = true)
            else {
                cancelPlaceSearch(); voiceDestinationBeforeArming = ""
                ui = ui.copy(destination = "", places = null, arrived = false, error = null)
                requestVoiceScreen("navigate")
                announce("Destination cleared. Say a new place or address.", interrupt = true)
            }
            AppVoiceAction.SEARCH -> if (ui.routeActive) announce("End your walk first to search for another place.", interrupt = true)
                else if (voiceDestinationBeforeArming.isBlank()) announce("Say the place or address you want to find.", interrupt = true)
                else { ui = ui.copy(destination = voiceDestinationBeforeArming, places = null, error = null); findPlaces(voiceDestinationBeforeArming) }
            AppVoiceAction.CANCEL_SEARCH -> {
                cancelPlaceSearch(); ui = ui.copy(places = null, error = null)
                announce("Search cancelled. Say a new destination, or Home navigation.", interrupt = true)
            }
            AppVoiceAction.STATUS -> when {
                ui.error != null -> announce(ui.error!!, interrupt = true)
                ui.routeActive -> performWalkAction(WalkVoiceAction.LOCATION)
                !destinationScreenReady -> performWalkAction(WalkVoiceAction.CAMERA_STATUS)
                else -> announce(if (ui.locationReady) "Location ready. You are on Navigate." else "Finding your location. You are on Navigate.", interrupt = true)
            }
            AppVoiceAction.CANCEL_INPUT -> announce("Voice command cancelled.", interrupt = true)
            else -> VoiceNavigation.external(this, action)
        }
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyVoiceNavigationIntent(intent)
    }

    private fun applyVoiceNavigationIntent(intent: Intent) {
        intent.getStringExtra(VoiceNavigation.SCREEN)?.takeIf { it in setOf("navigate", "camera") }?.let { screen ->
            if (!ui.routeActive) requestVoiceScreen(screen)
        }
        if (intent.getBooleanExtra(VoiceNavigation.BUTTON_SETUP, false)) ui = ui.copy(remoteSetupVisible = true)
        intent.removeExtra(VoiceNavigation.SCREEN)
        intent.removeExtra(VoiceNavigation.BUTTON_SETUP)
    }


    private fun armWisprInput() {
        voiceJob?.cancel()
        RemoteBridge.cancelSubmissionWait()
        prepareVoiceInput()
        // Instructions remain visible. Once armed, no app speech may enter Wispr.
    }

    private fun prepareVoiceInput() {
        if (!ui.voiceArmed) voiceDestinationBeforeArming = ui.destination
        ui = ui.copy(voiceKeyboardHidden = false)
        pendingScanUntil = 0L
        ui = if (ui.routeActive || !destinationScreenReady) ui.copy(walkCommandInput = "", error = null, status = "", voiceArmed = true, voiceRequest = ui.voiceRequest + 1)
        else if (ui.places != null) ui.copy(choiceInput = "", error = null, status = "", voiceArmed = true, voiceRequest = ui.voiceRequest + 1)
        else ui.copy(destination = "", error = null, status = "", voiceArmed = true, voiceRequest = ui.voiceRequest + 1)
        guidanceSpeech.silence()
        manualVoiceTimeout?.cancel()
        val request = ui.voiceRequest
        manualVoiceTimeout = lifecycleScope.launch {
            delay(45_000)
            // The bridge has its own recording/submission timeouts. This covers
            // arming the manual bubble and then leaving it without dictating.
            if (ui.voiceArmed && ui.voiceRequest == request && ui.remote.phase !in
                listOf(BridgePhase.PREPARING, BridgePhase.HOLDING, BridgePhase.SUBMITTING)) {
                voiceJob?.cancel()
                ui = ui.copy(voiceArmed = false)
                showError("Voice input timed out. Try speaking again.")
            }
        }
    }

    private fun searchDestination() {
        if (ui.voiceArmed && handleAppCommand(ui.destination)) return
        VoiceKeyboard.hide(this)
        voiceJob?.cancel()
        if (ui.voiceArmed) {
            val action = WalkVoiceCommands.resolve(ui.destination, false)
            if (action != WalkVoiceAction.UNKNOWN) {
                ui = ui.copy(voiceArmed = false, destination = voiceDestinationBeforeArming, voiceKeyboardHidden = true)
                when (action) {
                    WalkVoiceAction.SCAN, WalkVoiceAction.SHOW_CAMERA -> {
                        requestVoiceScreen("camera"); pendingScanUntil = android.os.SystemClock.elapsedRealtime() + 8_000
                        announce("Opening camera for object alerts.", interrupt = true)
                    }
                    WalkVoiceAction.LOCATION -> announce(if (ui.locationReady) "Phone location is ready. You are on Navigate."
                        else "Finding your phone location. Check Location settings if it remains unavailable.", interrupt = true)
                    WalkVoiceAction.CAMERA_STATUS -> announce(if (ui.cameraReady) "Camera scanning is ready. ${ui.cameraStatus}."
                        else "Camera is not scanning. Say open camera to start it.", interrupt = true)
                    WalkVoiceAction.REPEAT_ALERT -> announce(ui.lastWarning?.let { "Last alert. $it" } ?: "No object alert to repeat.", interrupt = true)
                    WalkVoiceAction.REPEAT -> repeatGuidance()
                    WalkVoiceAction.CLOSE_CONTROLS -> announce("Command cancelled. You are on Navigate.", interrupt = true)
                    WalkVoiceAction.END, WalkVoiceAction.KEEP_WALKING -> announce("There is no active outdoor walk.", interrupt = true)
                    WalkVoiceAction.HELP -> announce(AppVoiceCommands.navigationHelp, interrupt = true)
                    else -> announce("Start a walk before using the walking map and controls. Say a destination, or Home navigation.", interrupt = true)
                }
                return
            }
        }

        val destination = DestinationParser.parse(ui.destination)
        if (destination == null) { showError("Enter or speak a destination first."); return }
        if (VoicePhrases.normalize(destination) in setOf("nearest", "the nearest", "closest", "the closest", "nearby")) {
            showError("Say nearest followed by a place type, such as nearest toilet or nearest masjid."); return
        }
        ui = ui.copy(destination = destination, voiceArmed = false, error = null, arrived = false)
        findPlaces(destination)
    }

    private fun findPlaces(query: String) {
        val nearby = NearbyRequest.fromQuery(query)
        val category = nearby?.category
        ui = ui.copy(nearbyCategory = category, nearbyLabel = nearby?.label)
        if (!hasLocationPermission()) {
            pendingQuery = query
            permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        val origin = lastLocation?.takeIf { it.isRecentForSearch(query) }?.toGeoPoint()
        if (origin == null) {
            pendingQuery = query
            ui = ui.copy(busy = true, status = "Finding your location…", error = null)
            announce("Finding your location.")
            startLocationUpdates()
            locationTimeout?.cancel()
            locationTimeout = lifecycleScope.launch {
                delay(20_000)
                if (pendingQuery == query) {
                    pendingQuery = null
                    showError("Location unavailable. Turn on phone Location, allow precise location, then try near a window or outdoors.")
                }
            }
            return
        }
        pendingQuery = null
        networkJob?.cancel()
        ui = ui.copy(busy = true, status = "Searching for $query…", error = null, places = null)
        announce("Searching for $query.")
        networkJob = lifecycleScope.launch {
            try {
                val places = placeSearch.search(query, origin)
                if (places.isEmpty()) showError(if (nearby != null)
                    "No suggestions found within 5 km for ${nearby.label}. Try another place type or a specific name."
                    else "No matches found. Try a specific place name, area or city.")
                else {
                    ui = ui.copy(busy = false, status = "", places = places, choiceInput = "", voiceArmed = false)
                    readSuggestions()
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (nearby != null) Log.w("GuideLight", "Nearby search failed", error)
                showError(if (nearby != null) "Nearby search is unavailable. Check your connection or try again shortly."
                    else "Place search failed. Check your internet connection, then tap Find a place to retry.")
            }
        }
    }

    private fun requestWalkingRoute(place: PlaceResult) {
        voiceJob?.cancel()
        val origin = lastLocation?.toGeoPoint() ?: run { showError("Your location is unavailable. Try again outdoors."); return }
        ui = ui.copy(places = null, choiceInput = "", voiceArmed = false, busy = true, error = null, status = "Building your walk to ${place.name}…")
        announce("Building a walking route to ${place.name}.")
        networkJob?.cancel()
        networkJob = lifecycleScope.launch {
            try {
                val route = routing.route(origin, place.point)
                check(route.maneuvers.isNotEmpty())
                navigationSession = NavigationSession(route)
                val instruction = navigationSession!!.startingInstruction()
                ui = ui.copy(busy = false, status = "", routeActive = true, routeName = place.name,
                    routeShape = route.shape, turnGuidanceAvailable = false,
                    walkCommandInput = "", confirmEndWalk = false, voiceArmed = false,
                    walkingPanel = WalkingPanel.CAMERA, mapOverview = false, sceneSummary = null, primaryObjectDescription = null,
                    routeDistance = formatDistance(route.distanceKilometres * 1_000),
                    routeDuration = "About ${(route.durationSeconds / 60).coerceAtLeast(1)} min",
                    instruction = instruction, relativeRouteBearing = null, remainingDistance = "",
                    arrived = false, lastWarning = null, warningRisk = ObstacleRisk.NONE)
                updateScreenAwake()
                announce("Walking to ${place.name}. ${ui.routeDistance}. ${ui.routeDuration}. $instruction. You can use Wispr Flow to say repeat directions or end walk.", interrupt = true)
                refreshNavigation()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { showError("Could not build a walking route. Check your connection and try another place.") }
        }
    }

    private fun stopNavigation() {
        pendingNavigation = false; pendingScanUntil = 0L; pendingWarning = null
        objectAnnouncements.reset(); cameraSpeechPriority.clear()
        voiceJob?.cancel()
        RemoteBridge.release()
        navigationSession = null
        networkJob?.cancel()
        pendingQuery = null
        ui = ui.copy(routeActive = false, routeShape = emptyList(), groundPreview = emptyList(), busy = false, instruction = "", relativeRouteBearing = null, remainingDistance = "", direction = GuidanceDirection.STRAIGHT, status = "Walk ended", lastWarning = null,
            walkCommandInput = "", confirmEndWalk = false, voiceArmed = false, error = null,
            walkingPanel = WalkingPanel.CAMERA, sceneSummary = null, primaryObjectDescription = null)
        pathOverlay?.setRoute(false, GuidanceDirection.STRAIGHT)
        updateScreenAwake()
        announce("Walk ended.", interrupt = true)
    }

    private fun showError(message: String) {
        ui = ui.copy(busy = false, status = "", error = message)
        announce(message)
    }

    private fun hasPermission(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    private fun hasLocationPermission() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    override fun onStart() {
        super.onStart()
        ui = ui.copy(remotePermissionGranted = RemoteBridge.isPermissionEnabled(this))
        if (ui.remoteEnabled) RemoteBridge.attach(this, this)
        cameraHeading.start()
        updateScreenAwake()
        getSystemService(InputManager::class.java).registerInputDeviceListener(this, null)
        refreshSelfieConnection(announceChange = false)
        ui = ui.copy(cameraAllowed = hasPermission(Manifest.permission.CAMERA),
            preciseLocationAllowed = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
        if (::locationTracker.isInitialized && hasLocationPermission()) startLocationUpdates()
    }

    override fun onStop() {
        buttonConnectionCue.clear()
        remoteStartJob?.cancel()
        RemoteBridge.detach(this)
        pendingNavigation = false; pendingScanUntil = 0L; pendingWarning = null
        objectAnnouncements.reset(); cameraSpeechPriority.clear()
        sceneAt = 0L
        ui = ui.copy(sceneSummary = null, primaryObjectDescription = null)
        cameraHeading.stop()
        ui = ui.copy(relativeRouteBearing = null)
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(this)
        if (::locationTracker.isInitialized) locationTracker.stop()
        textToSpeech?.stop()
        super.onStop()
    }

    private fun updateScreenAwake() {
        if (ui.remoteEnabled || ui.routeActive) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val foundProvider = locationTracker.start()
        if (!foundProvider && pendingQuery != null) {
            pendingQuery = null
            showError("Phone Location is turned off. Open location settings to enable it.")
        }
    }

    private fun onLocationChanged(location: Location) {
        fun Location.fix() = LocationFix(toGeoPoint(), if (hasAccuracy()) accuracy else Float.NaN,
            elapsedRealtimeNanos / 1_000_000)
        if (!acceptsLocationFix(lastLocation?.fix(), location.fix(), android.os.SystemClock.elapsedRealtime())) return
        lastLocation = location
        cameraHeading.locate(location)
        ui = ui.copy(locationReady = true)
        val query = pendingQuery
        if (query == null) locationTimeout?.cancel()
        else if (location.isRecentForSearch(query)) {
            locationTimeout?.cancel()
            pendingQuery = null
            findPlaces(query)
        }
        refreshNavigation()
    }

    private fun Location.isRecentForSearch(query: String): Boolean {
        if (NearbyRequest.fromQuery(query) == null) return true
        val age = (android.os.SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000
        return age in 0..30_000
    }

    private fun refreshNavigation(speak: Boolean = true) {
        val session = navigationSession ?: return
        val location = lastLocation ?: return
        val age = (android.os.SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        val heading = cameraHeading.current()
        val mapHeading = if (location.hasBearing() && location.hasSpeed() && location.speed >= .7f &&
            location.hasBearingAccuracy() && location.bearingAccuracyDegrees in 0f..30f)
            location.bearing.toDouble() else heading
        session.update(location.toGeoPoint(), heading, location.accuracy,
            fresh = age in 0..10_000)?.let { update ->
            ui = ui.copy(instruction = update.instruction, direction = update.direction,
                walkingPosition = WalkingPosition(location.toGeoPoint(), location.accuracy, age !in 0..10_000, mapHeading),
                turnGuidanceAvailable = update.guidanceAvailable,
                navigationArrowBearing = update.arrowBearing,
                routeDuration = "About ${((update.remainingSeconds + 59) / 60).coerceAtLeast(1)} min",
                relativeRouteBearing = update.relativeBearing, groundPreview = update.groundPreview,
                remainingDistance = update.remainingMetres.takeIf { it > 0 }?.let(::formatDistance).orEmpty())
            if (update.arrived) {
                voiceJob?.cancel()
                RemoteBridge.release()
                navigationSession = null
                ui = ui.copy(routeActive = false, arrived = true, direction = GuidanceDirection.STRAIGHT,
                    relativeRouteBearing = null, walkCommandInput = "", confirmEndWalk = false, voiceArmed = false)
                updateScreenAwake()
            }
            if (speak) update.announcement?.let {
                if (cameraSpeechPriority.blocksNavigation(android.os.SystemClock.elapsedRealtime())) pendingNavigation = true
                else announce(it)
            }
        }
    }

    private fun onObjectsDetected(boxes: List<NormalizedBox>) {
        val now = android.os.SystemClock.elapsedRealtime()
        val objects = ObjectDescriptions.observations(boxes)
        val focused = ObjectDescriptions.observations(CameraAlertGrid.focused(boxes))
        val focusedRisk = ObstacleEvaluator.evaluate(boxes)
        sceneAt = now
        val summary = ObjectDescriptions.summary(objects)
        if (ui.sceneSummary != summary || ui.primaryObjectDescription != focused.firstOrNull()?.description())
            ui = ui.copy(sceneSummary = summary, primaryObjectDescription = focused.firstOrNull()?.description())
        if (pendingWarning?.first?.appearance?.let { it !in focused } == true || focused.isEmpty()) pendingWarning = null
        // Keep a muted, already-confirmed warning fresh only while the same
        // object is still visible. A long dictation must not consume its repeat window.
        if (pendingWarning != null && focusedRisk.risk != ObstacleRisk.NONE &&
            (cameraDictationActive() || !ttsReady)) pendingWarning = focusedRisk to now
        val available = (ui.routeActive || !destinationScreenReady) && ui.cameraReady && ttsReady && !cameraDictationActive() &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (available && pendingScanUntil > now) {
            pendingScanUntil = 0L
            announceCamera(summary)
            return
        }
        val canSpeak = available && focusedRisk.risk == ObstacleRisk.NONE &&
            now - lastRiskAt >= 4_500 && !cameraSpeechPriority.blocksNavigation(now)
        objectAnnouncements.offer(focused, now, canSpeak)?.let { announceCamera(it) }
    }

    private fun warnAboutObstacle(alert: ObstacleAlert) {
        if (!ui.routeActive && destinationScreenReady) return
        if (alert.risk == ObstacleRisk.NONE) {
            pendingWarning = null
            ui = ui.copy(warningRisk = ObstacleRisk.NONE, alertVisible = false)
            return
        }
        lastRiskAt = android.os.SystemClock.elapsedRealtime()
        val message = ObjectDescriptions.warning(alert)
        ui = ui.copy(lastWarning = message, warningRisk = alert.risk, alertVisible = true)
        val vibrator = getSystemService(Vibrator::class.java)
        if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createWaveform(
            if (alert.risk == ObstacleRisk.STOP) longArrayOf(0, 220, 100, 220) else longArrayOf(0, 180), -1))
        if (cameraDictationActive() || !ttsReady) pendingWarning = alert to lastRiskAt
        else announceCamera(message)
    }

    private fun cameraDictationActive() = ui.voiceArmed || RemoteBridge.status.value.phase in
        listOf(BridgePhase.PREPARING, BridgePhase.HOLDING, BridgePhase.SUBMITTING)

    private fun announceCamera(message: String) {
        if (!ttsReady || cameraDictationActive() || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (message == lastCameraMessage && cameraSpeechPriority.blocksNavigation(android.os.SystemClock.elapsedRealtime())) return
        lastCameraMessage = message
        if (ui.routeActive && textToSpeech?.isSpeaking == true) pendingNavigation = true
        cameraMessage = true
        try { announce(message, interrupt = true) } finally { cameraMessage = false }
    }

    private fun drainCameraSpeech() {
        val now = android.os.SystemClock.elapsedRealtime()
        pendingWarning?.let { (alert, at) ->
            if (now - at > 1_500) pendingWarning = null
            else if (!cameraDictationActive() && ttsReady) {
                pendingWarning = null; announceCamera(ObjectDescriptions.warning(alert)); return
            }
        }
        if (pendingScanUntil != 0L && now > pendingScanUntil) {
            pendingScanUntil = 0L
            announceCamera("Object scanning is not ready. Point the rear camera ahead and try again.")
        }
        if (pendingNavigation && ui.routeActive && !cameraDictationActive() && ttsReady &&
            !cameraSpeechPriority.blocksNavigation(now) && textToSpeech?.isSpeaking == false &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            pendingNavigation = false; announce(ui.instruction)
        }
    }

    private fun announce(message: String, interrupt: Boolean = false) {
        lastSpoken = message
        if (interrupt) guidanceSpeech.silence()
        guidanceSpeech.say(message)
    }

    private fun announceSequence(messages: List<String>) {
        lastSpoken = messages.joinToString(" ")
        val utterances = messages.flatMap { it.chunked(TextToSpeech.getMaxSpeechInputLength()) }
        guidanceSpeech.sequence(utterances)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    if (id?.startsWith("camera-") == true) Log.i("GuideSpeech", "Camera speech started")
                    if (id?.startsWith("button-") == true) Log.i("GuideSpeech", "GuideLight button speech started")
                }
                override fun onDone(id: String?) {
                    if (id?.startsWith("camera-") == true) Log.i("GuideSpeech", "Camera speech completed")
                    if (id?.startsWith("button-") == true) Log.i("GuideSpeech", "GuideLight button speech completed")
                    complete(id)
                }
                @Deprecated("Deprecated by Android") override fun onError(id: String?) = complete(id)
                override fun onStop(id: String?, interrupted: Boolean) = complete(id)
                private fun complete(id: String?) { runOnUiThread { cameraSpeechPriority.finished(id); drainCameraSpeech() } }
            })
            val result = textToSpeech?.setLanguage(Locale.US)
            ttsReady = result != null && result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (ttsReady) {
                if (ui.places != null) readSuggestions()
                else {
                    val greeting = "GuideLight ready. Say a destination, Home navigation for your rooms, or what can I say to hear the commands."
                    val connection = buttonConnectionCue.take(ui.remoteEnabled, !cameraDictationActive())
                    if (connection != null) announceButton("$connection $greeting") else announce(greeting)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("destination", ui.destination)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        cameraMonitor?.stop()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)
}
