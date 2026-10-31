package com.guidelight.app.indoor

import android.hardware.input.InputManager
import android.view.KeyEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.lifecycle.*
import com.guidelight.app.remote.*
import com.guidelight.app.ui.VoiceKeyboard
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

data class HomeVoiceState(val text: String = "", val armed: Boolean = false, val request: Int = 0, val message: String = "", val submitted: Boolean = false, val hideKeyboard: Boolean = false)

/** Reuses the same real Wispr overlay bridge and two-click Selfie protocol as outdoor navigation. */
class HomeVoiceInput(
    private val activity: ComponentActivity,
    private val available: () -> Boolean,
    private val silence: () -> Unit,
    private val vibrate: (Long) -> Unit,
    private val onCommand: (String) -> Unit,
    private val bridgeState: StateFlow<BridgeStatus> = RemoteBridge.status,
    private val feedback: (String) -> Unit = {}
) : DefaultLifecycleObserver, InputManager.InputDeviceListener {
    var state by mutableStateOf(HomeVoiceState())
        private set
    private val gate = RemoteHoldGate()
    private var sessionDevice: Int? = null
    private var remoteStart: Job? = null
    private var debounce: Job? = null
    private var timeout: Job? = null
    private var implicitInput = false
    private val original = activity.window.callback
    private val callback = object : Window.Callback by original {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean = handleRemoteKey(event) || original.dispatchKeyEvent(event)
    }
    private val inputs = activity.getSystemService(InputManager::class.java)

    init {
        activity.window.callback = callback
        activity.lifecycle.addObserver(this)
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                bridgeState.collect { bridge ->
                    if (!state.armed) return@collect
                    when {
                        !bridge.connected && sessionDevice != null -> cancel("Button control disconnected. Check Accessibility settings.")
                        bridge.phase == BridgePhase.ERROR && sessionDevice != null -> cancel(bridge.message)
                        bridge.phase == BridgePhase.HOLDING -> { state = state.copy(message = "Listening…"); vibrate(60) }
                        bridge.phase == BridgePhase.SUBMITTING -> {
                            state = state.copy(message = "Receiving your words…", hideKeyboard = true)
                            VoiceKeyboard.hide(activity)
                            vibrate(120)
                            schedule()
                        }
                        // A short SUBMITTING transition can be coalesced by StateFlow.
                        bridge.phase == BridgePhase.IDLE -> schedule()
                    }
                }
            }
        }
    }

    fun arm() {
        if (!available()) return
        implicitInput = false
        debounce?.cancel(); timeout?.cancel()
        RemoteBridge.cancelSubmissionWait()
        state = HomeVoiceState(armed = true, request = state.request + 1,
            message = "Use the Wispr Flow bubble, then speak.")
        silence()
        val request = state.request
        timeout = activity.lifecycleScope.launch {
            delay(45_000)
            if (state.armed && state.request == request) this@HomeVoiceInput.cancel("Voice input timed out. Try again.")
        }
    }

    fun changeText(text: String) {
        val old = state.text
        // Wispr can insert a complete phrase directly into the focused field without
        // the user touching our Speak action. Accept that new batch as a new command.
        // Single-key typing stays manual, so a pause in a partly typed room name
        // cannot silently save that partial name.
        if (!state.armed && available() && text.isNotBlank() && text.length - old.length > 1) {
            implicitInput = true
            state = state.copy(armed = true, submitted = false, message = "Receiving your words…")
            silence()
        } else if (implicitInput && text != old && kotlin.math.abs(text.length - old.length) <= 1) {
            implicitInput = false
            debounce?.cancel()
            state = state.copy(armed = false, message = "")
        }
        state = state.copy(text = text, submitted = false)
        if (state.armed && text.length - old.length > 1 && bridgeState.value.phase !in listOf(BridgePhase.PREPARING, BridgePhase.HOLDING)) {
            state = state.copy(hideKeyboard = true)
            VoiceKeyboard.hide(activity)
        }
        if (state.armed) schedule()
    }

    private fun schedule() {
        debounce?.cancel()
        if (!state.armed || state.text.isBlank() || bridgeState.value.phase in listOf(BridgePhase.PREPARING, BridgePhase.HOLDING)) return
        val text = state.text
        val request = state.request
        debounce = activity.lifecycleScope.launch {
            delay(1200)
            if (state.armed && state.text == text && state.request == request &&
                bridgeState.value.phase !in listOf(BridgePhase.PREPARING, BridgePhase.HOLDING)) submit()
        }
    }

    fun submit() {
        VoiceKeyboard.hide(activity)
        val text = state.text.trim()
        if (text.isBlank()) { cancel("Say a command first."); return }
        debounce?.cancel(); timeout?.cancel()
        implicitInput = false
        sessionDevice = null
        // Clear both Compose and IME surrounding text after consumption. The next
        // Wispr insertion starts at an empty field rather than appending a command.
        state = state.copy(text = "", armed = false, submitted = true, message = "Command received", hideKeyboard = true)
        RemoteBridge.transcriptReceived()
        onCommand(text)
    }

    internal fun handleRemoteKey(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP) return false
        if (event.action == KeyEvent.ACTION_UP && gate.up(event.deviceId)) {
            if (event.isCanceled) cancel("Button press interrupted. Try again.")
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val enabled = activity.getSharedPreferences("remote", 0).getBoolean("enabled", false) && available()
        return when (gate.down(event.deviceId, event.device?.name, event.repeatCount, enabled)) {
            RemoteHoldGate.Result.IGNORE -> false
            RemoteHoldGate.Result.REPEAT -> true
            RemoteHoldGate.Result.START -> {
                if (RemoteBridge.status.value.phase in listOf(BridgePhase.PREPARING, BridgePhase.HOLDING)) RemoteBridge.release()
                else if (!bridgeState.value.connected && !RemoteBridge.isPermissionEnabled(activity)) cancel(RemoteBridge.unavailableMessage(activity))
                else startRemoteInput(event.deviceId)
                true
            }
        }
    }

    private fun startRemoteInput(deviceId: Int) {
        if (remoteStart?.isActive == true) { cancel(); return }
        arm()
        sessionDevice = deviceId
        remoteStart = activity.lifecycleScope.launch {
            val ready = withTimeoutOrNull(3_000) { bridgeState.first { it.connected }; true } == true
            if (!state.armed || sessionDevice != deviceId || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            if (!ready || !RemoteBridge.begin()) cancel(if (!ready) RemoteBridge.unavailableMessage(activity) else "Button control is busy. Try again.")
        }
    }

    fun cancel(message: String = "") {
        VoiceKeyboard.hide(activity)
        remoteStart?.cancel()
        implicitInput = false
        debounce?.cancel(); timeout?.cancel()
        if (state.armed || sessionDevice != null) RemoteBridge.release()
        sessionDevice = null
        gate.cancel()
        state = state.copy(armed = false, message = message, hideKeyboard = true)
        if (message.isNotBlank()) feedback(message)
    }

    fun setRemoteEnabled(enabled: Boolean) {
        activity.getSharedPreferences("remote", 0).edit().putBoolean("enabled", enabled).apply()
        if (enabled && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) RemoteBridge.attach(activity, this)
        else if (!enabled) { cancel(); RemoteBridge.detach(this) }
    }

    override fun onResume(owner: LifecycleOwner) {
        if (activity.getSharedPreferences("remote", 0).getBoolean("enabled", false)) RemoteBridge.attach(activity, this)
        inputs.registerInputDeviceListener(this, null)
    }
    override fun onPause(owner: LifecycleOwner) {
        cancel()
        inputs.unregisterInputDeviceListener(this)
        RemoteBridge.detach(this)
    }
    override fun onDestroy(owner: LifecycleOwner) {
        cancel()
        RemoteBridge.detach(this)
        if (activity.window.callback === callback) activity.window.callback = original
        activity.lifecycle.removeObserver(this)
    }
    override fun onInputDeviceAdded(deviceId: Int) = Unit
    override fun onInputDeviceChanged(deviceId: Int) = Unit
    override fun onInputDeviceRemoved(deviceId: Int) {
        if (gate.activeDevice == deviceId || sessionDevice == deviceId) cancel("GuideLight button disconnected. Try again after reconnecting.")
    }
}
