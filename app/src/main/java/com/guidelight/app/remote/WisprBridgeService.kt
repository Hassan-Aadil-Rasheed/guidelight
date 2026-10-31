package com.guidelight.app.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** A user-enabled, foreground-only assistive adapter for Wispr's actual floating control. */
class WisprBridgeService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val readiness = BubbleReadinessGate()
    private var session = 0
    private var held = false
    private var startedAt = 0L
    private var target: Target? = null
    private var activeStroke: GestureDescription.StrokeDescription? = null
    private var gesturePending = false
    private var releaseRequested = false
    private var contactStartedAt = 0L
    private var lastReadiness = ""
    private val minimumContact get() = maxOf(700L, ViewConfiguration.getLongPressTimeout().toLong() + 200L)

    private data class Target(val windowId: Int, val x: Float, val y: Float)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i("GuideRemote", "service connected")
        RemoteBridge.service = this
        RemoteBridge.publish(BridgePhase.IDLE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    fun beginHold(): Boolean {
        if (held || activeStroke != null || gesturePending) return false
        if (!guideIsFocused() || isLocked()) return false
        held = true
        releaseRequested = false
        target = null
        readiness.clear()
        lastReadiness = ""
        startedAt = SystemClock.uptimeMillis()
        // Also invalidates a previous no-text timeout when the user retries.
        val token = ++session
        RemoteBridge.publish(BridgePhase.PREPARING, "Preparing Wispr…")
        // Let GuideLight focus its destination field and let Flow reveal its bubble.
        handler.postDelayed({ discoverTarget(token) }, 300)
        return true
    }

    private fun discoverTarget(token: Int) {
        if (token != session) return
        if (!held || !guideIsFocused() || isLocked()) {
            Log.i("GuideRemote", "Preparation stopped: held=$held guideFocused=${guideIsFocused()} locked=${isLocked()}")
            reset(); return
        }
        val bubble = findBubble()
        val now = SystemClock.uptimeMillis()
        val focusedEditor = windows.firstOrNull { it.isFocused && it.root?.packageName?.toString() == packageName }
            ?.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.isEditable == true
        val readinessState = "editor=$focusedEditor bubble=${bubble != null}"
        if (lastReadiness != readinessState) {
            lastReadiness = readinessState
            Log.i("GuideRemote", "Wispr readiness: $readinessState")
        }
        if (readiness.ready(bubble, focusedEditor, now) && now - startedAt >= 800 && bubble != null) {
            val settled = Target(bubble.id, (bubble.left + bubble.right) / 2f, (bubble.top + bubble.bottom) / 2f)
            target = settled
            val path = pointPath(settled)
            contactStartedAt = SystemClock.uptimeMillis()
            // Keep one pointer down. Do not dispatch empty stationary continuation
            // gestures: Android can optimise away all their events and cancel them.
            dispatch(token, GestureDescription.StrokeDescription(path, 0, 1, true), ending = false)
        } else if (now - startedAt < 4_000) {
            handler.postDelayed({ discoverTarget(token) }, 120)
        } else fail(if (focusedEditor && bubble == null)
            "Wispr's bubble is hidden. Open Wispr Flow, then return and try again."
            else "Wispr input is not ready. Tap the voice input field, then try again.")
    }

    private fun dispatch(token: Int, stroke: GestureDescription.StrokeDescription, ending: Boolean) {
        if (token != session) return
        activeStroke = stroke
        gesturePending = true
        val accepted = runCatching { dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (token != session) return
                gesturePending = false
                if (ending) {
                    Log.i("GuideRemote", "pointer up completed")
                    activeStroke = null
                    target = null
                    held = false
                    RemoteBridge.publish(BridgePhase.SUBMITTING, "Waiting for text. Press again to retry.")
                    handler.postDelayed({
                        if (token == session && RemoteBridge.status.value.phase == BridgePhase.SUBMITTING)
                            fail("No text received. Check Wispr Flow, then try again.")
                    }, 25_000)
                } else {
                    Log.i("GuideRemote", "pointer down completed; waiting for real-time long-press threshold")
                    maintainHold(token)
                }
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (token == session) fail("Button hold interrupted. Check Wispr Flow before trying again.")
            }
        }, handler) }.getOrDefault(false)
        if (!accepted) fail("Wispr button control failed. Check that Wispr has stopped recording before trying again.")
    }

    private fun maintainHold(token: Int) {
        if (token != session) return
        val bubble = target ?: return
        val previous = activeStroke ?: return
        val windowStillThere = windows.any { window ->
            // A recording control can animate or move while its pointer is held.
            // Geometry changes alone must not be treated as physical release.
            window.id == bubble.windowId
        }
        val now = SystemClock.uptimeMillis()
        val shouldRelease = releaseRequested || !held || isLocked() || !guideIsFocused() || !windowStillThere
        when (HoldReleasePolicy.action(now - contactStartedAt, now - startedAt, shouldRelease, minimumContact)) {
            HoldReleasePolicy.Action.RELEASE -> {
                val reason = when {
                    releaseRequested || !held -> "button"
                    isLocked() -> "locked"
                    !guideIsFocused() -> "background"
                    !windowStillThere -> "overlay removed"
                    else -> "time limit"
                }
                Log.i("GuideRemote", "release reason=$reason")
                dispatch(token, previous.continueStroke(pointPath(bubble), 0, 1, false), ending = true)
            }
            HoldReleasePolicy.Action.HELD -> {
                RemoteBridge.publish(BridgePhase.HOLDING, "Speak, then press the GuideLight button again to finish.")
                handler.postDelayed({ maintainHold(token) }, 80)
            }
            HoldReleasePolicy.Action.WAIT -> handler.postDelayed({ maintainHold(token) }, 40)
        }
    }

    fun releaseHold() {
        if (RemoteBridge.status.value.phase == BridgePhase.SUBMITTING) return
        held = false
        releaseRequested = true
        if (activeStroke == null) reset()
        // The real-time hold poll sends one final UP continuation. Before the long-
        // press threshold, release is deferred to prevent a tap-to-record session.
    }

    fun transcriptReceived() {
        cancelSubmissionWait()
    }

    fun cancelSubmissionWait() {
        if (RemoteBridge.status.value.phase == BridgePhase.SUBMITTING && !gesturePending) reset()
    }

    private fun findBubble(): BubbleWindow? {
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val candidates = windows.mapNotNull { window ->
            if (window.type != AccessibilityWindowInfo.TYPE_SYSTEM && window.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return@mapNotNull null
            val root = window.root ?: return@mapNotNull null
            if (!root.isVisibleToUser) return@mapNotNull null
            val rootPackage = root.packageName?.toString()
            if (rootPackage != FLOW_PACKAGE && window.title?.toString() != FLOW_PACKAGE) return@mapNotNull null
            val bounds = Rect().also(window::getBoundsInScreen)
            BubbleWindow(window.id, true, bounds.left, bounds.top, bounds.right, bounds.bottom)
        }
        // Ambiguity fails closed. Never guess a coordinate in another app.
        return BubbleTargetSelector.select(candidates, screenWidth, screenHeight, density)
    }

    private fun guideIsFocused(): Boolean = windows.any { it.isFocused && it.root?.packageName?.toString() == packageName }
    private fun isLocked(): Boolean = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != false
    private fun pointPath(point: Target) = Path().apply { moveTo(point.x, point.y) }

    private fun reset() {
        session++
        held = false
        activeStroke = null
        gesturePending = false
        target = null
        RemoteBridge.publish(BridgePhase.IDLE)
    }

    private fun fail(message: String) {
        Log.w("GuideRemote", message)
        session++
        held = false
        activeStroke = null
        gesturePending = false
        target = null
        RemoteBridge.publish(BridgePhase.ERROR, message)
    }

    override fun onInterrupt() { releaseHold() }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        // A late destruction callback from an old instance must not clear a newer connection.
        if (RemoteBridge.service === this) {
            RemoteBridge.service = null
            RemoteBridge.publish(BridgePhase.IDLE)
        }
        super.onDestroy()
    }

    companion object { const val FLOW_PACKAGE = "com.wispr.flowapp" }
}
