package com.guidelight.app.indoor

import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.ar.core.*
import com.guidelight.app.ui.GuideLightTheme
import io.github.sceneview.ar.ARSceneView
import java.util.Locale

/** Separate camera owner: CameraX's outdoor detector is not mounted in this activity. */
class MarkerTestActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var running by mutableStateOf(false)
    private var status by mutableStateOf(MarkerStatus())
    private var speaker: TextToSpeech? = null
    private var speechReady = false
    private var anchor: Anchor? = null
    private val measurement = MarkerMeasurement()
    private var lastUiUpdate = 0L
    private var lastAnnouncement = 0L
    private var frameSeen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        speaker = TextToSpeech(this, this)
        setContent {
            GuideLightTheme {
                MarkerScreen(
                    status, running,
                    onStart = {
                        clearReference()
                        status = MarkerStatus()
                        running = true
                        say("Point the camera at the Earth picture. Keep the whole picture in view.")
                    },
                    onStop = { stopTest() },
                    onBack = { finish() },
                    onRepeat = { say(status.spoken()) },
                    reference = { modifier ->
                        val picture = remember {
                            assets.open("indoor/home-marker.jpg").use(android.graphics.BitmapFactory::decodeStream).asImageBitmap()
                        }
                        Image(picture, "Earth picture to print at 18 centimetres wide", modifier)
                    },
                    camera = { modifier ->
                        AndroidView(
                            modifier = modifier.testTag("marker-camera"),
                            factory = { context ->
                                object : ARSceneView(context, sharedActivity = this@MarkerTestActivity, sharedLifecycle = lifecycle,
                                    sessionConfiguration = { session, config ->
                                        config.planeFindingMode = Config.PlaneFindingMode.DISABLED
                                        config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                                        config.focusMode = Config.FocusMode.AUTO
                                        config.augmentedImageDatabase = assets.open("indoor/home-marker.imgdb").use {
                                            AugmentedImageDatabase.deserialize(session, it)
                                        }
                                        // Check the device at runtime; this test doesn't consume depth images yet.
                                        val depth = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                                        status = status.copy(depthSupported = depth)
                                        Log.i("GuideIndoor", "Session configured; depthSupported=$depth")
                                    },
                                    onSessionResumed = {
                                        frameSeen = false
                                        Log.i("GuideIndoor", "AR camera resumed")
                                    },
                                    onSessionPaused = {
                                        clearReference()
                                        status = status.copy(phase = if (status.phase == MarkerPhase.ERROR) MarkerPhase.ERROR
                                            else MarkerPhase.SEARCHING, metres = null, cameraReady = false)
                                    },
                                    onSessionFailed = { error ->
                                        clearReference()
                                        status = status.copy(phase = MarkerPhase.ERROR, metres = null,
                                            problem = "Indoor tracking could not start. Check camera permission and Google Play Services for AR, then try again.")
                                        Log.w("GuideIndoor", "Session failed: ${error.javaClass.simpleName}")
                                        say(status.problem!!)
                                    },
                                    onSessionUpdated = { _, frame -> updateTracking(frame) }
                                ) {
                                    override fun destroy() {
                                        // Stop releases the camera while this activity is still resumed.
                                        // Pause synchronously before SceneView closes ARCore in the background.
                                        if (!isDestroyed) runCatching { session?.pause() }
                                        super.destroy()
                                    }
                                }.apply { planeRenderer.isEnabled = false }
                            },
                            onRelease = { view ->
                                clearReference()
                                view.destroy()
                            }
                        )
                    }
                )
            }
        }
    }

    private fun updateTracking(frame: Frame) {
        if (!running || status.phase == MarkerPhase.ERROR) return
        if (!frameSeen) {
            frameSeen = true
            status = status.copy(cameraReady = true)
            Log.i("GuideIndoor", "First AR camera frame received")
        }
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) {
            if (anchor != null) loseTracking()
            val hint = when (camera.trackingFailureReason) {
                TrackingFailureReason.INSUFFICIENT_LIGHT -> "More light is needed."
                TrackingFailureReason.EXCESSIVE_MOTION -> "Move the phone more slowly."
                TrackingFailureReason.INSUFFICIENT_FEATURES -> "Point at the picture and nearby textured surfaces."
                TrackingFailureReason.CAMERA_UNAVAILABLE -> "The camera is unavailable. Close other camera apps."
                else -> "Hold the phone steady and keep the picture in view."
            }
            if (status.hint != hint) status = status.copy(hint = hint)
            return
        }
        if (anchor == null) {
            val image = frame.getUpdatedTrackables(AugmentedImage::class.java).firstOrNull {
                it.name == "home-earth-18cm" && it.trackingState == TrackingState.TRACKING &&
                    it.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING
            } ?: return
            val newAnchor = image.createAnchor(image.centerPose)
            if (newAnchor.trackingState != TrackingState.TRACKING) {
                newAnchor.detach()
                return
            }
            anchor = newAnchor
            measurement.begin(newAnchor.pose.inverse().transformPoint(camera.pose.translation))
            status = status.copy(phase = MarkerPhase.TRACKING, metres = 0f,
                hint = "Move the phone slowly. The picture must stay fixed.")
            Log.i("GuideIndoor", "Marker acquired")
            announceChange("Marker found. Tracking is on. Move the phone slowly.")
        }
        val reference = anchor ?: return
        if (reference.trackingState != TrackingState.TRACKING) {
            loseTracking()
            return
        }
        val displacement = measurement.displacement(reference.pose.inverse().transformPoint(camera.pose.translation))
        if (displacement == null) {
            loseTracking()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastUiUpdate >= 250L) {
            lastUiUpdate = now
            status = status.copy(metres = displacement)
        }
    }

    private fun loseTracking() {
        clearReference()
        status = status.copy(phase = MarkerPhase.LOST, metres = null,
            hint = "Point at the Earth picture again.")
        Log.i("GuideIndoor", "Tracking lost; measurement cleared")
        // Loss must interrupt even if recognition was announced only a moment ago.
        say("Tracking lost. Point at the Earth picture again.")
    }

    private fun clearReference() {
        anchor?.detach()
        anchor = null
        measurement.clear()
        lastUiUpdate = 0L
    }

    private fun stopTest() {
        running = false
        clearReference()
        status = MarkerStatus()
        say("Indoor test stopped.")
    }

    private fun announceChange(message: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnnouncement >= 3_000L) {
            lastAnnouncement = now
            say(message)
        }
    }

    private fun say(message: String) {
        if (speechReady && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            speaker?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "indoor-status")
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            speechReady = speaker?.setLanguage(Locale.ENGLISH) in listOf(TextToSpeech.LANG_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE)
        }
    }

    override fun onPause() {
        speaker?.stop()
        clearReference()
        status = status.copy(phase = MarkerPhase.SEARCHING, metres = null)
        super.onPause()
    }

    override fun onDestroy() {
        clearReference()
        speaker?.shutdown()
        super.onDestroy()
    }
}

enum class MarkerPhase { SEARCHING, TRACKING, LOST, ERROR }

data class MarkerStatus(
    val phase: MarkerPhase = MarkerPhase.SEARCHING,
    val metres: Float? = null,
    val cameraReady: Boolean = false,
    val depthSupported: Boolean? = null,
    val hint: String = "Keep the whole Earth picture in view.",
    val problem: String? = null
) {
    val title: String get() = when (phase) {
        MarkerPhase.SEARCHING -> "Scan the picture"
        MarkerPhase.TRACKING -> "Marker found"
        MarkerPhase.LOST -> "Tracking lost"
        MarkerPhase.ERROR -> "Unable to start"
    }
    fun spoken(): String = when (phase) {
        MarkerPhase.TRACKING -> "Marker found. Phone displacement since scan, about ${String.format(Locale.US, "%.1f", metres ?: 0f)} metres."
        MarkerPhase.ERROR -> problem ?: title
        else -> "$title. $hint"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkerScreen(
    status: MarkerStatus, running: Boolean,
    onStart: () -> Unit, onStop: () -> Unit, onBack: () -> Unit, onRepeat: () -> Unit,
    reference: @Composable (Modifier) -> Unit,
    camera: @Composable (Modifier) -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        TopAppBar(title = { Text("Indoor test") }, navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Navigate")
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!running) {
                Text("One picture. One fixed spot.", style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() })
                reference(Modifier.fillMaxWidth().height(170.dp))
                Text("Print the Earth picture at 18 × 18 cm. Fix it flat on a wall near your entrance in good light.",
                    style = MaterialTheme.typography.bodyLarge)
                Text("For this first test, scan the picture and move the phone a little. You can also display the picture on your laptop, at exactly 18 cm wide.")
                Text("This tests tracking only. Room saving, directions and obstacle warnings are not active here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("start-marker-test")) {
                    Text("Start camera test")
                }
                Text("This application runs on Google Play Services for AR (ARCore), which is provided by Google and governed by the Google Privacy Policy.",
                    style = MaterialTheme.typography.bodySmall)
                val context = androidx.compose.ui.platform.LocalContext.current
                TextButton(onClick = {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://policies.google.com/privacy")))
                }) { Text("Google Privacy Policy") }
            } else {
                Text(status.title, style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag("marker-status").semantics { heading() })
                if (status.phase != MarkerPhase.ERROR) camera(Modifier.fillMaxWidth().height(300.dp))
                if (status.metres != null && status.phase == MarkerPhase.TRACKING) {
                    Text(String.format(Locale.US, "≈ %.1f m", status.metres), style = MaterialTheme.typography.displaySmall)
                    Text("Distance from scan position · estimate")
                }
                Text(status.problem ?: status.hint, style = MaterialTheme.typography.bodyLarge)
                if (status.cameraReady) Text("AR camera ready", modifier = Modifier.testTag("ar-camera-ready"),
                    style = MaterialTheme.typography.bodySmall)
                status.depthSupported?.let {
                    Text(if (it) "Depth is supported on this phone. It is not used in this test."
                        else "Depth is not available on this phone.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onRepeat, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Read status aloud") }
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("stop-marker-test")) { Text("Stop test") }
            }
        }
    }
}
