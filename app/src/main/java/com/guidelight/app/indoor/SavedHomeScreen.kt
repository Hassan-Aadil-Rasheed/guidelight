package com.guidelight.app.indoor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.guidelight.app.ui.GuideSpace
import com.guidelight.app.vision.LocalAiTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedHomeScreen(
    state: SavedHomeState, voice: HomeVoiceState, onBack: () -> Unit,
    onCreateMap: () -> Unit, onFind: () -> Unit, onSave: () -> Unit, onStop: () -> Unit,
    onSpeak: () -> Unit, onText: (String) -> Unit, onSubmit: () -> Unit, onLegacy: () -> Unit,
    onAi: (LocalAiTask) -> Unit, onAiSetup: () -> Unit, onAiCancel: () -> Unit,
    onCloudSetup: () -> Unit, camera: @Composable (Modifier) -> Unit
) {
    val busy = state.stage in setOf(SavedHomeStage.PREPARING, SavedHomeStage.SAVING, SavedHomeStage.CLOSING)
    var moreOptions by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.optionsRequest) { if (state.optionsRequest > 0) moreOptions = state.optionsExpanded }
    val colors = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier.imePadding(), containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Home", style = MaterialTheme.typography.titleLarge)
                        if (fontScale <= 1.3f) Text("Room recognition", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Navigate")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            // Bound both pinned areas so the action list remains reachable with large text or an IME.
            // The native camera stays mounted outside the scrolling instruction/action list.
            val voiceLimit = maxHeight * 0.45f
            val previewHeight = (if (voice.armed || fontScale > 1.3f) 100.dp else 180.dp)
                .coerceAtMost(maxHeight * 0.28f)
            Column(Modifier.widthIn(max = GuideSpace.contentWidth).fillMaxSize().align(Alignment.TopCenter)) {
                if (!busy) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = voiceLimit)
                            .verticalScroll(rememberScrollState()).padding(horizontal = GuideSpace.page, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSpeak, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("saved-map-speak"),
                            shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(16.dp)
                        ) {
                            Icon(Icons.Outlined.Mic, null, Modifier.size(26.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Speak a command", style = MaterialTheme.typography.titleMedium)
                                Text("Wispr Flow", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HomeCommandEditor(voice, onText, onSubmit)
                        if (voice.message.isNotBlank()) Text(
                            voice.message, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
                if (state.preview) {
                    Box(
                        Modifier.padding(horizontal = GuideSpace.page).fillMaxWidth().height(previewHeight)
                            .clip(MaterialTheme.shapes.medium).background(colors.surface)
                    ) {
                        camera(Modifier.fillMaxSize().testTag("saved-map-camera"))
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = GuideSpace.page, vertical = 8.dp)
                            .semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.CenterFocusStrong, null, Modifier.size(18.dp), tint = colors.primary)
                        Text(state.cameraStatus, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f).testTag("saved-map-camera-status"))
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(GuideSpace.page),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(1.dp, colors.outlineVariant), shape = MaterialTheme.shapes.large
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.AutoAwesome, null, tint = colors.primary)
                                Text("Camera assistant", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            }
                            Text(
                                if (state.aiMessage == "Cloudflare is ready") "Ready to describe your surroundings" else state.aiMessage,
                                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                                modifier = Modifier.testTag("local-ai-status").semantics { liveRegion = LiveRegionMode.Polite }
                            )
                            if (state.aiBusy) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                OutlinedButton(onClick = onAiCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Cancel analysis") }
                            } else if (!state.aiReady) {
                                FilledTonalButton(onClick = onAiSetup, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Set up local AI") }
                            } else {
                                Button(
                                    onClick = { onAi(LocalAiTask.DESCRIBE) }, enabled = state.preview,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("home-describe"),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Icon(Icons.Outlined.Visibility, null); Spacer(Modifier.width(10.dp)); Text("Describe view")
                                }
                                OutlinedButton(
                                    onClick = { onAi(LocalAiTask.IDENTIFY_ROOM) }, enabled = state.preview && state.hasMap,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("home-identify"),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Icon(Icons.Outlined.Home, null); Spacer(Modifier.width(10.dp)); Text("Identify room")
                                }
                                if (!state.hasMap) Text("Save a home map to identify rooms.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                            if (state.aiAnswer.isNotBlank()) Surface(color = colors.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                Text(state.aiAnswer, Modifier.fillMaxWidth().padding(14.dp).testTag("local-ai-answer"), style = MaterialTheme.typography.bodyLarge)
                            }
                            TextButton(
                                onClick = { moreOptions = !moreOptions },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("home-more-options")
                                    .semantics { stateDescription = if (moreOptions) "Expanded" else "Collapsed" }
                            ) {
                                Text(if (moreOptions) "Fewer options" else "More options")
                                Spacer(Modifier.width(8.dp))
                                Icon(if (moreOptions) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                            }
                            if (moreOptions) {
                                HorizontalDivider(color = colors.outlineVariant)
                                TextButton(onClick = onCloudSetup, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Icon(Icons.Outlined.Settings, null); Spacer(Modifier.width(8.dp))
                                    Text(if (state.cloudReady) "Cloudflare settings" else "Connect Cloudflare")
                                }
                                if (state.aiReady && !state.aiBusy) {
                                    TextButton(onClick = { onAi(LocalAiTask.EXPLAIN_STATUS) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Explain my status") }
                                    TextButton(onClick = { onAi(LocalAiTask.DETAILED_VIEW) }, enabled = state.preview,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Detailed analysis (slow)") }
                                }
                            }
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = colors.surface), border = BorderStroke(1.dp, colors.outlineVariant)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Your home map", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            Text(state.message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.testTag("saved-map-status"))
                            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            when (state.stage) {
                                SavedHomeStage.READY, SavedHomeStage.ERROR -> {
                                    if (state.hasMap) FilledTonalButton(onClick = onFind, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("saved-map-find")) { Text("Find my position") }
                                    OutlinedButton(onClick = onCreateMap, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("saved-map-record")) {
                                        Text(if (state.hasMap) "Create another home map" else "Create home map")
                                    }
                                    Text("With a helper, record connected rooms and their doorways. Your map stays on this phone. Cloudflare comparisons send room thumbnails when connected.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                    Text("Camera tracking uses Google Play Services for AR, governed by Google's Privacy Policy.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                    val context = LocalContext.current
                                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://policies.google.com/privacy"))) }) { Text("Google Privacy Policy") }
                                }
                                SavedHomeStage.RECORDING -> {
                                    Text("Views recorded: ${state.views}", modifier = Modifier.testTag("saved-map-views"))
                                    Text(if (state.rooms.isEmpty()) "Say “This is my kitchen” or the room's name." else "Rooms marked: ${state.rooms.joinToString()}", style = MaterialTheme.typography.bodyMedium)
                                    Button(onClick = onSave, enabled = state.views >= 10 && state.rooms.size >= 3,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("saved-map-save")) { Text("Save home map") }
                                }
                                else -> Unit
                            }
                            if (state.preview && !busy) OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("End test") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Info, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text("Recognition trial only. Walking directions and obstacle avoidance are not active on this screen.",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    if (!state.preview) TextButton(onClick = onLegacy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Previous room recordings") }
                }
            }
        }
    }
}
