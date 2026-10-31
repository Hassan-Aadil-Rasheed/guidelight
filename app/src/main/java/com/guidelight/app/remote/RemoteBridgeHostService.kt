package com.guidelight.app.remote

import android.app.Service
import android.content.Intent
import android.os.*
import kotlinx.coroutines.*

/** Non-exported IPC endpoint, colocated with accessibility in a small :button process. */
class RemoteBridgeHostService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val clients = mutableSetOf<Messenger>()
    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            // The manifest forbids external binding; also reject messages from another UID.
            if (message.sendingUid != Process.myUid()) return
            when (message.what) {
                BridgeProtocol.REGISTER -> message.replyTo?.let { client ->
                    clients += client
                    reply(client, RemoteBridge.status.value)
                }
                BridgeProtocol.UNREGISTER -> message.replyTo?.let(clients::remove)
                BridgeProtocol.BEGIN -> if (!RemoteBridge.begin()) {
                    message.replyTo?.let { reply(it, BridgeStatus(RemoteBridge.service != null, BridgePhase.ERROR,
                        "Button control is busy or GuideLight is not in front. Try again.")) }
                }
                BridgeProtocol.RELEASE -> RemoteBridge.release()
                BridgeProtocol.TRANSCRIPT -> RemoteBridge.transcriptReceived()
                BridgeProtocol.CANCEL_WAIT -> RemoteBridge.cancelSubmissionWait()
            }
        }
    })

    override fun onCreate() {
        super.onCreate()
        scope.launch { RemoteBridge.status.collect { status ->
            clients.toList().forEach { reply(it, status) }
        } }
    }

    private fun reply(client: Messenger, status: BridgeStatus) {
        val message = BridgeProtocol.status(status).apply { data.putInt(BridgeProtocol.PROCESS_ID, Process.myPid()) }
        if (runCatching { client.send(message) }.isFailure) clients -= client
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onUnbind(intent: Intent): Boolean {
        RemoteBridge.release()
        clients.clear()
        return false
    }

    override fun onDestroy() {
        RemoteBridge.release()
        scope.cancel()
        super.onDestroy()
    }
}
