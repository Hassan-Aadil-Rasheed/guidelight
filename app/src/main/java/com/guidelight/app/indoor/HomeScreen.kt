package com.guidelight.app.indoor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState, voice: HomeVoiceState,
    onBack: () -> Unit, onStart: () -> Unit, onStopCamera: () -> Unit,
    onSpeak: () -> Unit, onText: (String) -> Unit, onSubmit: () -> Unit,
    onChoose: (HomeRoom) -> Unit,
    onList: () -> Unit, onRepeat: () -> Unit, onEnd: () -> Unit,
    camera: @Composable (Modifier) -> Unit
) {
    BackHandler(onBack = onBack)
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(voice.request, voice.armed, voice.hideKeyboard) {
        if (voice.armed && state.cameraRunning && !voice.hideKeyboard) { focus.requestFocus(); keyboard?.show() } else {
            if (!voice.armed && voice.hideKeyboard) focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    Scaffold(modifier = Modifier.imePadding(), topBar = {
        TopAppBar(title = { Text("Home") }, navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Navigate")
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            // Keep the AR SurfaceView in the visible window when the IME opens.
            // A preview at the bottom of the scroll can lose its surface while
            // Wispr focuses the editor, interrupting marker/path tracking.
            if (state.cameraRunning) {
                camera(Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 20.dp).testTag("home-camera"))
                Text(state.trackingText, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("home-tracking"))
            }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (state.mode == HomeMode.WALKING) "To ${state.destination}" else "Your rooms",
                style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            if (!state.cameraRunning) {
                Button(onClick = onSpeak, enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("home-speak-idle")) {
                    Icon(Icons.Outlined.Mic, null); Spacer(Modifier.width(12.dp)); Text("Speak a command")
                }
                Text("Keep your chosen Earth picture fixed upright at the entrance, 18 cm wide. Scan it each time you open Home.")
                val context = LocalContext.current
                val markerNames = if (state.map.nodes.isEmpty()) listOf(HomeMarkers.ORIGINAL, HomeMarkers.PERSONAL)
                    else listOf(state.map.marker)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                    markerNames.forEach { name ->
                        val marker = remember(name) {
                            val asset = if (name == HomeMarkers.PERSONAL) "home-marker-personal.jpg" else "home-marker.jpg"
                            context.assets.open("indoor/$asset").use(android.graphics.BitmapFactory::decodeStream).asImageBitmap()
                        }
                        Image(marker, "Supported Earth picture for the fixed home marker", Modifier.size(100.dp))
                    }
                }
                Button(onClick = onStart, enabled = state.loaded, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("home-start")) { Text("Scan home marker") }
                Text("Walk with a helper. Stop in each room and say “This is my kitchen” or its name. It saves automatically. Keep the phone facing forward at chest height.")
                Text("This application runs on Google Play Services for AR (ARCore), which is provided by Google and governed by the Google Privacy Policy.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://policies.google.com/privacy"))) }) { Text("Google Privacy Policy") }
            } else {
                // This editor stays mounted across recording, room choices and walking.
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onSpeak, enabled = !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("home-speak")) {
                            Icon(Icons.Outlined.Mic, null); Spacer(Modifier.width(12.dp)); Text("Speak with Wispr Flow")
                        }
                        if (voice.message.isNotBlank()) Text(voice.message, style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(value = voice.text, onValueChange = onText,
                            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("home-command"),
                            label = { Text("Your command") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { keyboard?.hide(); onSubmit() }))
                        if (!voice.armed && !voice.submitted && voice.text.isNotBlank()) TextButton(onClick = { keyboard?.hide(); onSubmit() }, enabled = !state.saving) { Text("Send command") }
                    }
                }
                if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.instruction, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("home-instruction"))
                state.speechProblem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.remaining?.let { Text(String.format(Locale.US, "≈ %.1f m remaining", it), style = MaterialTheme.typography.headlineMedium) }
                state.warning?.let { Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Text(it, Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                } }
                if (state.mode == HomeMode.WALKING) {
                    Button(onClick = onEnd, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("home-end")) { Text("End walk") }
                    OutlinedButton(onClick = onRepeat, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Repeat directions") }
                } else OutlinedButton(onClick = onList, enabled = !voice.armed,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Hear my rooms") }
                Text(if (state.cameraReady) if (state.objectScanReady) "Camera on · object scan on" else "Camera on · object scan unavailable"
                    else "Starting camera…", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(if (state.cameraReady) "home-camera-ready" else "home-camera-starting"))
                TextButton(onClick = onStopCamera, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Stop camera") }
            }
            if (state.map.rooms.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Saved rooms", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onList, enabled = !voice.armed) { Text("Read aloud") }
                }
                state.map.rooms.forEachIndexed { index, room ->
                    OutlinedButton(onClick = { onChoose(room) }, enabled = state.cameraRunning && !state.saving && !voice.armed,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("home-room-$index")) { Text("${index + 1}. ${room.name}") }
                }
            }
            Text("Assisted indoor prototype · one floor. Object warnings can miss furniture and small hazards. Keep using your cane or guide dog.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
    }
}
