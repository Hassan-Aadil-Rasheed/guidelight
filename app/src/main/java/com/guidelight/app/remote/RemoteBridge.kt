package com.guidelight.app.remote

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BridgePhase { IDLE, PREPARING, HOLDING, SUBMITTING, ERROR }
data class BridgeStatus(val connected: Boolean = false, val phase: BridgePhase = BridgePhase.IDLE, val message: String = "")

internal object BridgeProtocol {
    const val REGISTER = 1
    const val UNREGISTER = 2
    const val BEGIN = 3
    const val RELEASE = 4
    const val TRANSCRIPT = 5
    const val CANCEL_WAIT = 6
    const val STATUS = 7
    const val TEXT = "message"
    const val PROCESS_ID = "process_id"

    fun status(value: BridgeStatus) = Message.obtain(null, STATUS, value.phase.ordinal, if (value.connected) 1 else 0).apply {
        data = Bundle().apply { putString(TEXT, value.message) }
    }
}

/** The small button process owns gestures; activities reconnect through a private Messenger. */
object RemoteBridge {
    // This reference exists only in the :button process. Activities never own the accessibility service.
    internal var service: WisprBridgeService? = null
    private val mutableStatus = MutableStateFlow(BridgeStatus())
    val status = mutableStatus.asStateFlow()
    private val owners = mutableSetOf<Any>()
    private var application: Context? = null
    private var host: Messenger? = null
    private var bound = false
    @Volatile internal var transportProcessId = 0
        private set
    private val replies = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.what != BridgeProtocol.STATUS || owners.isEmpty()) return
            val phase = BridgePhase.entries.getOrNull(message.arg1) ?: BridgePhase.ERROR
            transportProcessId = message.data.getInt(BridgeProtocol.PROCESS_ID)
            mutableStatus.value = BridgeStatus(message.arg2 == 1, phase, message.data.getString(BridgeProtocol.TEXT).orEmpty())
        }
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            host = Messenger(binder)
            send(BridgeProtocol.REGISTER)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            host = null
            transportProcessId = 0
            mutableStatus.value = BridgeStatus()
            // Android reconnects this binding when the private host returns.
        }
        override fun onBindingDied(name: ComponentName) {
            host = null
            transportProcessId = 0
            mutableStatus.value = BridgeStatus()
            if (bound) runCatching { application?.unbindService(this) }
            bound = false
            if (owners.isNotEmpty()) bind()
        }
        override fun onNullBinding(name: ComponentName) { onBindingDied(name) }
    }

    fun attach(context: Context, owner: Any) {
        application = context.applicationContext
        owners += owner
        if (!bound) bind()
    }

    fun detach(owner: Any) {
        owners -= owner
        if (owners.isNotEmpty()) return
        send(BridgeProtocol.RELEASE)
        send(BridgeProtocol.UNREGISTER)
        host = null
        transportProcessId = 0
        if (bound) runCatching { application?.unbindService(connection) }
        bound = false
        mutableStatus.value = BridgeStatus()
    }

    private fun bind() {
        val context = application ?: return
        bound = runCatching {
            context.bindService(Intent(context, RemoteBridgeHostService::class.java), connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
    }

    private fun send(command: Int): Boolean {
        val target = host ?: return false
        return runCatching {
            target.send(Message.obtain(null, command).apply { replyTo = replies })
            true
        }.getOrElse {
            host = null
            mutableStatus.value = BridgeStatus()
            false
        }
    }

    internal fun publish(phase: BridgePhase, message: String = "") {
        if (mutableStatus.value.phase != phase) Log.i("GuideRemote", "phase=$phase")
        mutableStatus.value = BridgeStatus(service != null, phase, message)
    }

    fun isPermissionEnabled(context: Context): Boolean {
        val component = ComponentName(context, WisprBridgeService::class.java)
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    fun unavailableMessage(context: Context): String = if (isPermissionEnabled(context))
        "Button control is already enabled, but Android stopped it. Open GuideLight button control in Accessibility and restart it once."
    else "Enable GuideLight button control in Accessibility settings."

    fun begin(): Boolean = service?.beginHold() ?: (status.value.connected && send(BridgeProtocol.BEGIN))
    fun release() { service?.releaseHold() ?: send(BridgeProtocol.RELEASE) }
    fun transcriptReceived() { service?.transcriptReceived() ?: send(BridgeProtocol.TRANSCRIPT) }
    fun cancelSubmissionWait() { service?.cancelSubmissionWait() ?: send(BridgeProtocol.CANCEL_WAIT) }
}
