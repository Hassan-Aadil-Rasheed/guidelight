package com.guidelight.app.vision

import android.os.SystemClock
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.ar.core.*
import com.guidelight.app.domain.*
import com.guidelight.app.indoor.ArObjectMonitor
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.delay

@Composable
fun OutdoorRouteCamera(activity: ComponentActivity, modifier: Modifier, relativeBearing: Double?,
    groundPreview: List<Pair<Float, Float>>, direction: GuidanceDirection, beforeCreate: () -> Unit = {},
    onObjects: (List<NormalizedBox>) -> Unit = {}, onStatus: (Boolean, String) -> Unit, onRisk: (ObstacleAlert) -> Unit) {
    val bearing by rememberUpdatedState(relativeBearing)
    val preview by rememberUpdatedState(groundPreview)
    val status by rememberUpdatedState(onStatus)
    val risk by rememberUpdatedState(onRisk)
    val observations by rememberUpdatedState(onObjects)
    val owner = remember { arrayOfNulls<OutdoorCameraBinding>(1) }
    LaunchedEffect(activity) {
        activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(500)
                owner[0]?.let {
                    if (it.lastFrameAt != 0L && SystemClock.elapsedRealtime() - it.lastFrameAt > 1500) {
                        it.lastFrameAt = 0
                        it.overlay.setGroundRibbon(null); it.objects.invalidate()
                        status(false, "Camera tracking paused")
                    }
                }
            }
        }
    }
    AndroidView(modifier = modifier, factory = { context ->
        beforeCreate()
        FrameLayout(context).apply {
            val overlay = PathGuidanceOverlay(context).apply { useGroundProjection = true; minimalDetections = true }
            val projector = GroundRouteProjector()
            var objectsReady = false
            val objects = ArObjectMonitor(context, { boxes -> overlay.setDetections(boxes); observations(boxes) },
                { risk(it) }, { objectsReady = it })
            val scene = object : ARSceneView(context, sharedActivity = activity, sharedLifecycle = activity.lifecycle,
                sessionConfiguration = { _, config ->
                    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                    config.focusMode = Config.FocusMode.AUTO
                }, onSessionUpdated = { session, frame ->
                    owner[0]?.lastFrameAt = SystemClock.elapsedRealtime()
                    val ribbon = projector.project(frame, width, height, bearing, preview)
                    overlay.setGroundRibbon(ribbon)
                    objects.offer(session, frame, display?.rotation ?: 0)
                    val text = when {
                        frame.camera.trackingState != TrackingState.TRACKING -> "Camera tracking paused"
                        !objectsReady -> "Starting object scanning…"
                        bearing == null -> "Waiting for your direction"
                        ribbon == null -> "Point camera at ground ahead"
                        else -> "Route guide · ground detected"
                    }
                    status(objectsReady, text)
                }, onSessionPaused = { objects.invalidate(); overlay.setGroundRibbon(null); status(false, "Camera paused") },
                onSessionFailed = { objects.invalidate(); overlay.setGroundRibbon(null); status(false, "Ground guide unavailable") }
            ) {
                override fun destroy() {
                    if (!isDestroyed) runCatching { session?.pause() }
                    super.destroy()
                }
            }.apply { planeRenderer.isEnabled = false }
            addView(scene, FrameLayout.LayoutParams(-1, -1)); addView(overlay, FrameLayout.LayoutParams(-1, -1))
            tag = OutdoorCameraBinding(scene, overlay, objects).also { owner[0] = it }
        }
    }, update = { view ->
        (view.tag as OutdoorCameraBinding).let {
            it.overlay.setRoute(true, direction)
            if (relativeBearing == null) it.overlay.setGroundRibbon(null)
        }
    }, onRelease = { view ->
        owner[0] = null
        (view.tag as OutdoorCameraBinding).let { it.objects.close(); it.scene.destroy() }
    })
}

private data class OutdoorCameraBinding(val scene: ARSceneView, val overlay: PathGuidanceOverlay,
    val objects: ArObjectMonitor, var lastFrameAt: Long = 0)
