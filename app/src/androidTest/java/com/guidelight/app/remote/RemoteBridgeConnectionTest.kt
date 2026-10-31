package com.guidelight.app.remote

import android.content.ComponentName
import android.os.Process
import android.os.SystemClock
import android.hardware.input.InputManager
import androidx.test.core.app.ActivityScenario
import com.guidelight.app.MainActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteBridgeConnectionTest {
    @Test fun connectedButtonNameStartsAndCompletesRealSpeechOnMainScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val inputs = context.getSystemService(InputManager::class.java)
        assertTrue("Reconnect the real GuideLight button for this opt-in check",
            inputs.inputDeviceIds.any { GuideLightButton.matches(inputs.getInputDevice(it)?.name) })
        assertTrue(context.getSharedPreferences("remote", 0).getBoolean("enabled", false))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val deadline = SystemClock.elapsedRealtime() + 25_000
            var completed = false
            while (!completed && SystemClock.elapsedRealtime() < deadline) {
                val descriptor = instrumentation.uiAutomation.executeShellCommand("logcat -d --pid=${Process.myPid()} -s GuideSpeech:I")
                val log = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
                completed = log.contains("GuideLight button speech started") && log.contains("GuideLight button speech completed")
                if (!completed) SystemClock.sleep(250)
            }
            assertTrue("The TTS engine did not complete the new-name announcement", completed)
        } finally { scenario.close() }
    }

    @Test fun privateTransportRunsOutsideTheCameraProcessAndReconnectsWithoutChangingOptIn() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val before = context.getSharedPreferences("remote", 0).getBoolean("enabled", false)
        val service = context.packageManager.getServiceInfo(ComponentName(context, RemoteBridgeHostService::class.java), 0)
        assertFalse(service.exported)
        assertEquals(context.packageName + ":button", service.processName)
        val firstOwner = Any()
        val secondOwner = Any()
        try {
            instrumentation.runOnMainSync { RemoteBridge.attach(context, firstOwner) }
            awaitTransport()
            assertNotEquals(Process.myPid(), RemoteBridge.transportProcessId)
            instrumentation.runOnMainSync { RemoteBridge.detach(firstOwner) }
            assertEquals(0, RemoteBridge.transportProcessId)
            instrumentation.runOnMainSync { RemoteBridge.attach(context, secondOwner) }
            awaitTransport()
            assertNotEquals(Process.myPid(), RemoteBridge.transportProcessId)
            assertEquals(before, context.getSharedPreferences("remote", 0).getBoolean("enabled", false))
        } finally {
            instrumentation.runOnMainSync {
                RemoteBridge.detach(firstOwner)
                RemoteBridge.detach(secondOwner)
            }
        }
    }

    private fun awaitTransport() {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (RemoteBridge.transportProcessId == 0 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("The private button process did not reply", RemoteBridge.transportProcessId > 0)
    }
}
