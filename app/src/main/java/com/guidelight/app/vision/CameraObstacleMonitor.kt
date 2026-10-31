package com.guidelight.app.vision

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.guidelight.app.domain.NormalizedBox
import com.guidelight.app.domain.ObstacleEvaluator
import com.guidelight.app.domain.ObstacleAlert
import com.guidelight.app.domain.ObstacleAlertGate

class CameraObstacleMonitor(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onReady: () -> Unit,
    private val onDetections: (List<NormalizedBox>) -> Unit,
    private val onWarning: (ObstacleAlert) -> Unit,
    private val onError: (String) -> Unit
) {
    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
    )

    private var controller: LifecycleCameraController? = null
    private var stopped = false
    private val alertGate = ObstacleAlertGate()

    fun start() {
        try {
            val executor = ContextCompat.getMainExecutor(context)
            controller = LifecycleCameraController(context).apply {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                setImageAnalysisAnalyzer(executor, MlKitAnalyzer(
                    listOf(detector), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor
                ) { result ->
                    if (stopped || previewView.width == 0 || previewView.height == 0) return@MlKitAnalyzer
                    result.getThrowable(detector)?.let { onError("Path scanning paused."); return@MlKitAnalyzer }
                    val objects = result.getValue(detector) ?: return@MlKitAnalyzer
                    val boxes = objects.mapNotNull { detected ->
                        val rect = detected.boundingBox
                        NormalizedBox(
                            left = (rect.left.toFloat() / previewView.width).coerceIn(0f, 1f),
                            top = (rect.top.toFloat() / previewView.height).coerceIn(0f, 1f),
                            right = (rect.right.toFloat() / previewView.width).coerceIn(0f, 1f),
                            bottom = (rect.bottom.toFloat() / previewView.height).coerceIn(0f, 1f)
                        ).takeIf { it.width > 0f && it.height > 0f }
                    }
                    onDetections(boxes)
                    alertGate.offer(ObstacleEvaluator.evaluate(boxes), SystemClock.elapsedRealtime())?.let(onWarning)
                })
                previewView.controller = this
                bindToLifecycle(lifecycleOwner)
            }
            previewView.previewStreamState.observe(lifecycleOwner) { stream ->
                if (!stopped && stream == PreviewView.StreamState.STREAMING) onReady()
            }
        } catch (error: Exception) {
            onError("Camera could not start.")
        }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        previewView.previewStreamState.removeObservers(lifecycleOwner)
        controller?.clearImageAnalysisAnalyzer()
        controller?.unbind()
        previewView.controller = null
        detector.close()
    }

}

