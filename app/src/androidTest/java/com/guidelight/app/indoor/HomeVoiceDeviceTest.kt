package com.guidelight.app.indoor

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.guidelight.app.remote.BridgePhase
import com.guidelight.app.remote.BridgeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class HomeVoiceDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<HomeActivity>()

    @Test fun successiveWisprBatchesClearOldTextWithoutTheOptionalBridge() {
        val phase = MutableStateFlow(BridgeStatus(connected = false))
        val commands = CopyOnWriteArrayList<String>()
        lateinit var input: HomeVoiceInput
        compose.runOnIdle {
            input = HomeVoiceInput(compose.activity, { true }, {}, {}, { commands += it }, phase)
            input.changeText("List my rooms")
        }
        compose.waitUntil(5000) { commands.size == 1 }
        compose.runOnIdle {
            assertThat(input.state.text).isEmpty()
            input.changeText("Repeat directions")
        }
        compose.waitUntil(5000) { commands.size == 2 }
        compose.runOnIdle {
            assertThat(commands).containsExactly("List my rooms", "Repeat directions").inOrder()
            assertThat(input.state.text).isEmpty()
            assertThat(input.state.armed).isFalse()
        }
    }

    @Test fun singleKeyTypingDoesNotAutosubmitAPartialRoomName() {
        val phase = MutableStateFlow(BridgeStatus(connected = false))
        val commands = CopyOnWriteArrayList<String>()
        lateinit var input: HomeVoiceInput
        compose.runOnIdle {
            input = HomeVoiceInput(compose.activity, { true }, {}, {}, { commands += it }, phase)
            "This is my kitchen".runningFold("") { prefix, key -> prefix + key }.forEach(input::changeText)
        }
        compose.runOnIdle {
            assertThat(input.state.armed).isFalse()
            assertThat(commands).isEmpty()
            input.submit()
            assertThat(commands).containsExactly("This is my kitchen")
            assertThat(input.state.text).isEmpty()
        }
    }

    @Test fun textArrivingDuringHoldSubmitsWhenBridgeReturnsStraightToIdle() {
        val phase = MutableStateFlow(BridgeStatus(connected = true))
        val commands = CopyOnWriteArrayList<String>()
        lateinit var input: HomeVoiceInput
        compose.runOnIdle {
            input = HomeVoiceInput(compose.activity, { true }, {}, {}, { commands += it }, phase)
            input.arm()
            phase.value = BridgeStatus(true, BridgePhase.HOLDING)
        }
        compose.runOnIdle { input.changeText("List my rooms") }
        compose.runOnIdle { assertThat(commands).isEmpty() }
        compose.runOnIdle { phase.value = BridgeStatus(true, BridgePhase.IDLE) }
        compose.waitUntil(5000) { commands.isNotEmpty() }
        compose.runOnIdle {
            assertThat(commands).containsExactly("List my rooms")
            assertThat(input.state.submitted).isTrue()
            assertThat(input.state.armed).isFalse()
        }
    }
}
