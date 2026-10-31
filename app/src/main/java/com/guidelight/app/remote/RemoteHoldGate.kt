package com.guidelight.app.remote

/** Own exactly one physical press. Never consume the phone's own volume buttons. */
class RemoteHoldGate {
    private var device: Int? = null
    val activeDevice: Int? get() = device

    fun down(deviceId: Int, deviceName: String?, repeat: Int, enabled: Boolean): Result {
        if (device == deviceId) return Result.REPEAT
        if (!enabled || repeat != 0 || device != null || !GuideLightButton.matches(deviceName)) return Result.IGNORE
        device = deviceId
        return Result.START
    }

    fun up(deviceId: Int): Boolean {
        if (device != deviceId) return false
        device = null
        return true
    }

    fun cancel(): Boolean = (device != null).also { device = null }
    enum class Result { START, REPEAT, IGNORE }
}
