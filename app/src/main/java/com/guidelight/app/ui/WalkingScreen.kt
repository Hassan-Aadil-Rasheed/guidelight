package com.guidelight.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guidelight.app.domain.GuidanceDirection
import com.guidelight.app.domain.ObstacleRisk
import com.guidelight.app.domain.WalkVoiceAction
import com.guidelight.app.domain.WalkVoiceCommands
import com.guidelight.app.remote.BridgePhase

private val CameraChrome = Color(0xF011120F)

/** One camera and one persistent Wispr editor; panels never replace the activity or camera. */
@Composable
internal fun WalkingScreen(state: GuideUiState, actions: GuideActions, cameraContent: @Composable (Modifier) -> Unit) {
    val capturing = state.remote.phase in listOf(BridgePhase.PREPARING, BridgePhase.HOLDING)
    BackHandler {
        if (state.walkingPanel != WalkingPanel.CAMERA) actions.onWalkAction(WalkVoiceAction.CLOSE_CONTROLS)
        else actions.onStopConfirmation(true)
    }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).imePadding().testTag("walking-camera-screen")) {
        val panelHeight = maxHeight * .54f
        val instructionHeight = maxHeight * .29f
        val commandHeight = maxHeight * .52f
        if (state.cameraAllowed) cameraContent(Modifier.fillMaxSize().testTag("walking-camera-preview"))
        else Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.NoPhotography, null, tint = Color.White)
            Text("Allow camera access to scan the view", color = Color.White)
            Button(onClick = actions.onCameraPermission, modifier = Modifier.heightIn(min = 56.dp)) { Text("Camera settings") }
        }

        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledIconButton(onClick = { actions.onWalkAction(if (state.walkingPanel == WalkingPanel.CONTROLS)
                    WalkVoiceAction.CLOSE_CONTROLS else WalkVoiceAction.OPEN_CONTROLS) },
                    modifier = Modifier.size(56.dp).testTag("walk-menu"), shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = CameraChrome, contentColor = Color.White)) {
                    Icon(Icons.Outlined.Menu, "Walking controls")
                }
                Surface(color = CameraChrome, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("Walking to ${state.routeName}", style = MaterialTheme.typography.titleSmall, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                        Text("${state.remainingDistance.ifBlank { state.routeDistance }} • ${state.routeDuration}",
                            style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
                FilledIconButton(onClick = actions.onSpeak, enabled = !capturing,
                    modifier = Modifier.size(56.dp).testTag("walk-speak"), shape = CircleShape) {
                    Icon(Icons.Outlined.Mic, "Speak a command")
                }
            }
            Surface(color = CameraChrome, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.heightIn(max = instructionHeight).verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.turnGuidanceAvailable) {
                        val angle = state.navigationArrowBearing?.toFloat() ?: when (state.direction) {
                            GuidanceDirection.LEFT -> -90f; GuidanceDirection.RIGHT -> 90f; else -> 0f
                        }
                        Icon(Icons.Outlined.North, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp).rotate(angle).testTag("navigation-arrow"))
                    } else Icon(Icons.Outlined.LocationSearching, null, modifier = Modifier.size(40.dp))
                    Text(state.instruction, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).testTag("instruction").semantics { heading() })
                }
            }
        }

        if (state.walkingPanel != WalkingPanel.CAMERA && !state.confirmEndWalk) {
            Surface(color = CameraChrome, shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp)
                    .widthIn(max = 440.dp).fillMaxWidth().heightIn(max = panelHeight).testTag("walking-panel")) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(when (state.walkingPanel) {
                            WalkingPanel.MAP -> "Walking map"; WalkingPanel.HELP -> "Voice help"; else -> "Walking controls"
                        }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                        IconButton(onClick = { actions.onWalkAction(WalkVoiceAction.CLOSE_CONTROLS) }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Outlined.Close, "Close walking panel")
                        }
                    }
                    when (state.walkingPanel) {
                        WalkingPanel.MAP -> {
                            Text(state.walkingPosition?.label ?: "Finding your location")
                            if (state.routeShape.isNotEmpty()) WalkingMap(state, actions)
                            else Text("Route map is loading. Spoken directions remain available.")
                        }
                        WalkingPanel.HELP -> {
                            Text("Press the GuideLight button, speak with Wispr Flow, then press again to send. You can also tap the microphone.")
                            Text(WalkVoiceCommands.help(false))
                            Text("Objects are described left, ahead or right, and high, middle or low in the camera view. Small, medium and large describe appearance in the image, not real size or distance. Scanning can miss objects.")
                            Text("Keep using your cane or guide dog.")
                            OutlinedButton(onClick = { actions.onRemoteSetup(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("GuideLight button setup") }
                        }
                        else -> {
                            WalkingAction("Repeat directions", Icons.AutoMirrored.Outlined.VolumeUp) { actions.onRepeat() }
                            WalkingAction("Scan surroundings", Icons.Outlined.CenterFocusStrong) { actions.onWalkAction(WalkVoiceAction.SCAN) }
                            WalkingAction("Repeat alert", Icons.Outlined.WarningAmber) { actions.onWalkAction(WalkVoiceAction.REPEAT_ALERT) }
                            WalkingAction("Show map", Icons.Outlined.Map) { actions.onWalkAction(WalkVoiceAction.SHOW_MAP) }
                            WalkingAction("Location status", Icons.Outlined.MyLocation) { actions.onWalkAction(WalkVoiceAction.LOCATION) }
                            WalkingAction("Camera status", Icons.Outlined.Videocam) { actions.onWalkAction(WalkVoiceAction.CAMERA_STATUS) }
                            WalkingAction("Camera view", Icons.Outlined.CameraAlt) { actions.onWalkAction(WalkVoiceAction.SHOW_CAMERA) }
                            WalkingAction("Help and commands", Icons.AutoMirrored.Outlined.HelpOutline) { actions.onWalkAction(WalkVoiceAction.HELP) }
                            WalkingAction("End walk", Icons.Outlined.StopCircle) { actions.onStopConfirmation(true) }
                            if (state.walkingPosition?.precise != true) WalkingAction("Location settings", Icons.Outlined.Settings, actions.onLocationSettings)
                            Text("Keep using your cane or guide dog.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.lastWarning != null && state.alertVisible && !state.voiceArmed && !state.confirmEndWalk && state.walkingPanel == WalkingPanel.CAMERA) {
                Surface(color = if (state.warningRisk == ObstacleRisk.STOP) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().testTag("walking-alert")) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.WarningAmber, null)
                        Text(state.lastWarning, style = MaterialTheme.typography.bodyMedium, maxLines = 4,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
            WalkingVoiceControls(state, actions, commandHeight)
            if (!state.voiceArmed && !state.confirmEndWalk) Surface(color = CameraChrome, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(state.primaryObjectDescription?.takeIf { state.cameraReady } ?: state.cameraStatus,
                        style = MaterialTheme.typography.labelMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    val buttonStatus = when {
                        !state.remoteEnabled -> "Microphone to speak"
                        !state.remote.connected -> "Button service unavailable"
                        !state.selfieConnected -> "GuideLight button disconnected"
                        else -> "GuideLight button to speak"
                    }
                    Text("${state.walkingPosition?.label ?: "Finding location"} • $buttonStatus",
                        style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }
    }
    }
}

@Composable
private fun WalkingAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Icon(icon, null); Spacer(Modifier.width(12.dp)); Text(label, Modifier.weight(1f))
    }
}

@Composable
internal fun WalkingVoiceControls(state: GuideUiState, actions: GuideActions, maxHeight: androidx.compose.ui.unit.Dp) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val remoteBusy = state.remote.phase in listOf(BridgePhase.PREPARING, BridgePhase.HOLDING, BridgePhase.SUBMITTING)
    val visible = state.voiceArmed || state.confirmEndWalk || state.error != null || state.walkCommandInput.isNotBlank()
    LaunchedEffect(state.voiceRequest, state.voiceArmed, state.voiceKeyboardHidden) {
        if (state.voiceArmed && !state.voiceKeyboardHidden) {
            // A physical key can arm the editor while its idle layout is still
            // collapsed. Wait for its visible layout before starting the IME.
            withFrameNanos { }
            focus.requestFocus()
            withFrameNanos { }
            keyboard?.show()
        } else keyboard?.hide()
    }
    // Keep the exact editor mounted while panels and confirmation change. The remote
    // arms it before Wispr checks focus and waits for the keyboard/bubble to settle.
    Surface(color = if (state.routeActive) CameraChrome else MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(max = if (visible) maxHeight else 0.dp).clipToBounds()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.confirmEndWalk) {
                Text("End this walk?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text("Say yes or keep walking.")
                Button(onClick = { keyboard?.hide(); actions.onStop() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("confirm-end-walk")) { Text("End walk") }
                OutlinedButton(onClick = { actions.onStopConfirmation(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Keep walking") }
            }
            if (remoteBusy) Text(state.remote.message, style = MaterialTheme.typography.bodyMedium)
            else if (state.voiceArmed) Text("Use Wispr Flow to speak your command.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value = state.walkCommandInput, onValueChange = actions.onWalkCommand,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("walk-command"),
                label = { Text("Command") }, placeholder = { Text(if (state.confirmEndWalk) "Yes or keep walking" else "Scan, show map, repeat or end walk") },
                singleLine = true, isError = state.error != null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!remoteBusy) { keyboard?.hide(); actions.onSubmitWalkCommand() } }),
                trailingIcon = {
                    IconButton(onClick = { keyboard?.hide(); actions.onSubmitWalkCommand() }, enabled = state.walkCommandInput.isNotBlank() && !remoteBusy,
                        modifier = Modifier.size(56.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Send walk command") }
                })
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        }
    }
}
