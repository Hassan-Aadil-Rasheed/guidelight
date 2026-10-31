package com.guidelight.app.indoor

import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.ar.core.*
import com.guidelight.app.domain.*
import com.guidelight.app.vision.PathGuidanceOverlay
import io.github.sceneview.ar.ARSceneView

@Composable
fun HomeCamera(
    activity: ComponentActivity, modifier: Modifier, walking: Boolean, direction: GuidanceDirection,
    onFrame: (Session, Frame) -> Unit, onPaused: () -> Unit, onError: () -> Unit,
    onRisk: (ObstacleAlert) -> Unit, onObjectAvailability: (Boolean) -> Unit
) {
    AndroidView(modifier = modifier, factory = { context ->
        FrameLayout(context).apply {
            val overlay = PathGuidanceOverlay(context)
            val objects = ArObjectMonitor(context, overlay::setDetections, onRisk, onObjectAvailability)
            val scene = object : ARSceneView(context, sharedActivity = activity, sharedLifecycle = activity.lifecycle,
                sessionConfiguration = { session, config ->
                    config.planeFindingMode = Config.PlaneFindingMode.DISABLED
                    config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                    config.focusMode = Config.FocusMode.AUTO
                    config.augmentedImageDatabase = context.assets.open("indoor/home-markers.imgdb").use {
                        AugmentedImageDatabase.deserialize(session, it)
                    }
                },
                onSessionUpdated = { session, frame ->
                    onFrame(session, frame)
                    objects.offer(session, frame, display?.rotation ?: 0)
                },
                onSessionPaused = { objects.invalidate(); onPaused() },
                onSessionFailed = { objects.invalidate(); onError() }
            ) {
                override fun destroy() {
                    if (!isDestroyed) runCatching { session?.pause() }
                    super.destroy()
                }
            }.apply { planeRenderer.isEnabled = false }
            addView(scene, FrameLayout.LayoutParams(-1, -1))
            addView(overlay, FrameLayout.LayoutParams(-1, -1))
            overlay.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            tag = HomeCameraBinding(scene, overlay, objects)
        }
    }, update = { view ->
        (view.tag as HomeCameraBinding).let { binding ->
            binding.overlay.setRoute(walking, direction)
        }
    }, onRelease = { view ->
        (view.tag as HomeCameraBinding).let { it.objects.close(); it.scene.destroy() }
    })
}

private data class HomeCameraBinding(val scene: ARSceneView, val overlay: PathGuidanceOverlay, val objects: ArObjectMonitor)
