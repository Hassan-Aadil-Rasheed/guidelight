package com.guidelight.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.Accessible
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.guidelight.app.domain.GuidanceDirection
import com.guidelight.app.domain.ObstacleRisk
import com.guidelight.app.navigation.PlaceResult
import com.guidelight.app.navigation.NearbyCategory
import com.guidelight.app.remote.BridgePhase
import java.util.Locale

/** Material components own focus, touch semantics, navigation, fields and modal behaviour. */
@Composable
fun GuideLightApp(
    state: GuideUiState,
    actions: GuideActions,
    cameraContent: @Composable (Modifier) -> Unit = { CameraPlaceholder(it) }
) {
    val navigation = rememberNavController()
    val entry by navigation.currentBackStackEntryAsState()
    val current = entry?.destination?.route ?: "navigate"
    LaunchedEffect(state.screenRequest) {
        if (state.screenRequest > 0) navigation.navigate(state.requestedScreen) {
            popUpTo(navigation.graph.startDestinationId) { saveState = true }
            launchSingleTop = true; restoreState = true
        }
    }
    LaunchedEffect(current, state.routeActive) { actions.onDestinationScreenReady(state.routeActive || current == "navigate") }
    if (state.routeActive) {
        GuideLightTheme(darkTheme = true) { WalkingScreen(state, actions, cameraContent) }
    } else {
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        topBar = { AppHeader(actions.onHelp) },
        bottomBar = {
            if (!state.routeActive && state.places == null) {
                Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    listOf(
                        Triple("navigate", "Navigate", Icons.Outlined.NearMe),
                        Triple("camera", "Camera", Icons.Outlined.CenterFocusStrong)
                    ).forEach { (route, title, icon) ->
                        NavigationBarItem(
                            selected = current == route,
                            onClick = {
                                navigation.navigate(route) {
                                    popUpTo(navigation.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, null) }, label = { Text(title) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                }
            }
        }
    ) { padding ->
        NavHost(navigation, startDestination = "navigate", modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable("navigate") {
                DestinationScreen(state, actions)
            }
            composable("camera") { CameraScreen(state, actions, cameraContent) }
        }
    }
    }
    if (state.helpVisible) HelpDialog(onRemoteSetup = { actions.onRemoteSetup(true) }, onKey = actions.onHardwareKey, onSpeak = actions.onSpeak) { actions.onHelp(false) }
    if (state.remoteSetupVisible) RemoteSetup(state, actions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppHeader(onHelp: (Boolean) -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.NearMe, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                    }
                }
                Text("GuideLight", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        actions = {
            IconButton(onClick = { onHelp(true) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, "Help and app information", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}
@Composable
private fun DestinationScreen(state: GuideUiState, actions: GuideActions) {
    val choosing = state.places != null
    val capturing = state.remote.phase in listOf(BridgePhase.PREPARING, BridgePhase.HOLDING)
    val remoteBusy = state.remote.phase in listOf(BridgePhase.PREPARING, BridgePhase.HOLDING, BridgePhase.SUBMITTING)
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = choosing, onBack = actions.onDismissPlaces)
    LaunchedEffect(state.voiceRequest, state.voiceArmed, state.voiceKeyboardHidden) {
        if (state.voiceRequest > 0 && state.voiceArmed && !state.voiceKeyboardHidden) {
            focus.requestFocus()
            keyboard?.show()
        } else keyboard?.hide()
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = GuideSpace.page),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = GuideSpace.contentWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GuideSpace.section)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (choosing) "SEARCH RESULTS" else "PLAN YOUR WALK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(if (choosing) "Which place?" else "Where to?", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            }
            if (choosing) {
                Text((state.nearbyLabel ?: state.nearbyCategory?.plural)?.let { "Nearby $it" }
                    ?: "${state.places!!.size} ${if (state.places.size == 1) "suggestion" else "suggestions"} for “${state.destination}”",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.nearbyLabel != null || state.nearbyCategory != null) Text("Suggestions ordered by approximate distance. Walking distance may be longer.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!choosing) Text("Say a place, address or “nearest toilet”.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(GuideSpace.large), verticalArrangement = Arrangement.spacedBy(GuideSpace.large)) {
                    Button(
                        onClick = actions.onSpeak, enabled = !state.busy && !capturing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag(if (choosing) "choose-voice" else "speak"),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(GuideSpace.large)
                    ) {
                        Icon(Icons.Outlined.Mic, null, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(GuideSpace.medium))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text(if (choosing) "Choose by voice" else "Speak destination", style = MaterialTheme.typography.titleMedium)
                            Text("Voice input · Wispr Flow", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
                    }
                    if (choosing) FilledTonalButton(onClick = actions.onRepeatPlaces, enabled = !remoteBusy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = GuideSpace.control).testTag("repeat-suggestions")) {
                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, null)
                        Spacer(Modifier.width(GuideSpace.small))
                        Text("Repeat suggestions")
                    }
                    if (state.remote.phase in listOf(BridgePhase.PREPARING, BridgePhase.HOLDING, BridgePhase.SUBMITTING)) {
                        StatusNotice(state.remote.message, Icons.Outlined.Mic)
                    } else if (state.voiceArmed) {
                        StatusNotice(if (choosing) "Hold the Wispr Flow bubble, then say “Choose suggestion 1”." else "Hold the Wispr Flow bubble, then speak.", Icons.Outlined.Mic)
                    }
                    // Keep this exact editable node across search and suggestions.
                    // Wispr must not lose its insertion target when results arrive.
                    OutlinedTextField(
                        value = if (choosing) state.choiceInput else state.destination,
                        onValueChange = if (choosing) actions.onChoiceInput else actions.onDestination,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).focusRequester(focus).testTag(if (choosing) "choice-input" else "destination"),
                        label = { Text(if (choosing) "Your choice" else "Destination") },
                        placeholder = { Text(if (choosing) "Choose suggestion 1" else "Place or address") },
                        leadingIcon = { Icon(Icons.Outlined.Place, null) },
                        trailingIcon = {
                            if (choosing) IconButton(onClick = { keyboard?.hide(); actions.onSubmitChoice() }, enabled = state.choiceInput.isNotBlank() && !remoteBusy) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Use this choice")
                            }
                            else if (state.destination.isNotEmpty()) IconButton(onClick = { actions.onDestination("") }) {
                                Icon(Icons.Outlined.Close, "Clear destination")
                            }
                        },
                        singleLine = true, readOnly = state.busy, isError = state.error != null,
                        shape = MaterialTheme.shapes.small,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { if (!remoteBusy && !state.busy) {
                            keyboard?.hide()
                            if (choosing) actions.onSubmitChoice() else actions.onSearch()
                        } })
                    )
                    if (!choosing) Button(
                        onClick = { keyboard?.hide(); actions.onSearch() },
                        enabled = state.destination.isNotBlank() && !state.busy && !remoteBusy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = GuideSpace.control).testTag("search"),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Icon(Icons.Outlined.Search, null)
                        Spacer(Modifier.width(GuideSpace.small))
                        Text(if (!state.busy) "Find place" else if (state.status.startsWith("Building")) "Getting directions…" else if (!state.locationReady) "Getting location…" else "Searching…")
                    }
                    if ((state.status.isNotBlank() && !state.busy) || state.error != null) {
                        StatusNotice(state.error ?: state.status, if (state.error != null) Icons.Outlined.Info else Icons.Outlined.Place, isError = state.error != null)
                    }
                }
            }
            if (choosing) SuggestionCards(state.places!!, actions, remoteBusy)
            else Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surface).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(GuideSpace.small), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (state.locationReady) Icons.Outlined.MyLocation else Icons.Outlined.LocationSearching, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(if (state.locationReady) "Location ready" else "Waiting for location", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (!state.locationReady) TextButton(onClick = actions.onLocationSettings) { Text("Settings") }
            }
            if (state.arrived) StatusNotice("You have reached ${state.routeName}.", Icons.Outlined.CheckCircle)
            if (!choosing) OutlinedCard(
                onClick = { keyboard?.hide(); actions.onIndoorTest() },
                enabled = !state.busy && !state.voiceArmed && !remoteBusy,
                modifier = Modifier.fillMaxWidth().testTag("indoor-test"),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Home, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Home navigation", style = MaterialTheme.typography.titleMedium)
                        Text("Say “Home navigation” to open your rooms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SafetyNotice()
            Spacer(Modifier.height(GuideSpace.small))
        }
    }
}

@Composable
private fun CameraScreen(state: GuideUiState, actions: GuideActions, cameraContent: @Composable (Modifier) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(GuideSpace.page), verticalArrangement = Arrangement.spacedBy(GuideSpace.large)) {
        Text("Path ahead", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("camera-heading").semantics { heading() })
        Text("Point your camera forward.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = actions.onSpeak, modifier = Modifier.fillMaxWidth().heightIn(min = GuideSpace.control).testTag("camera-speak")) {
            Icon(Icons.Outlined.Mic, null); Spacer(Modifier.width(8.dp)); Text("Speak a command")
        }
        WalkingVoiceControls(state, actions, 320.dp)
        CameraCard(state, actions, Modifier.fillMaxWidth().height(320.dp), cameraContent)
        WarningNotice(state)
        FilledTonalButton(onClick = { actions.onWalkAction(com.guidelight.app.domain.WalkVoiceAction.REPEAT_ALERT) }, enabled = state.lastWarning != null, modifier = Modifier.fillMaxWidth().heightIn(min = GuideSpace.control)) {
            Icon(Icons.AutoMirrored.Outlined.VolumeUp, null)
            Spacer(Modifier.width(GuideSpace.small))
            Text("Repeat alert")
        }
        SafetyNotice()
    }
}

@Composable
private fun CameraCard(state: GuideUiState, actions: GuideActions, modifier: Modifier, cameraContent: @Composable (Modifier) -> Unit) {
    Box(modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.onSurface)) {
        if (state.cameraAllowed) cameraContent(Modifier.fillMaxSize())
        else Column(Modifier.align(Alignment.Center).padding(GuideSpace.large), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.NoPhotography, null, tint = MaterialTheme.colorScheme.surface)
            Text("Allow the camera in app permissions", color = MaterialTheme.colorScheme.surface)
            FilledTonalButton(onClick = actions.onCameraPermission) { Text("Camera settings") }
        }
        Surface(Modifier.align(Alignment.TopStart).padding(GuideSpace.medium), color = Color(0xE6171816), shape = MaterialTheme.shapes.small) {
            Row(Modifier.padding(GuideSpace.medium), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GuideSpace.small)) {
                Icon(Icons.Outlined.CenterFocusStrong, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(if (state.routeActive) state.cameraStatus else if (state.cameraReady) "Camera scanning" else state.cameraStatus, color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun WarningNotice(state: GuideUiState) {
    if (state.lastWarning == null) {
        return
    }
    val stop = state.warningRisk == ObstacleRisk.STOP
    Surface(color = if (stop) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().padding(GuideSpace.large).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(GuideSpace.medium)) {
            val color = if (stop) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
            Icon(Icons.Outlined.WarningAmber, null, tint = color)
            Column {
                Text("Last alert", style = MaterialTheme.typography.labelMedium, color = color)
                Text(state.lastWarning, style = MaterialTheme.typography.titleMedium, color = color)
            }
        }
    }
}

@Composable
private fun StatusNotice(text: String, icon: ImageVector, isError: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(color = if (isError) colors.errorContainer else colors.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().padding(GuideSpace.medium).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, horizontalArrangement = Arrangement.spacedBy(GuideSpace.small)) {
            Icon(icon, null, tint = if (isError) colors.onErrorContainer else colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = if (isError) colors.onErrorContainer else colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun SafetyNotice() {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(GuideSpace.small)) {
        Icon(Icons.AutoMirrored.Outlined.Accessible, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text("Keep using your cane or guide dog.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SuggestionCards(places: List<PlaceResult>, actions: GuideActions, remoteBusy: Boolean) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GuideSpace.medium)) {
            places.forEachIndexed { index, place ->
                OutlinedCard(
                    onClick = { keyboard?.hide(); actions.onChoosePlace(place) }, enabled = !remoteBusy,
                    modifier = Modifier.fillMaxWidth().testTag("place-$index"),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(Modifier.padding(GuideSpace.large), horizontalArrangement = Arrangement.spacedBy(GuideSpace.medium), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GuideSpace.xSmall)) {
                            Text("Suggestion ${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(place.name, style = MaterialTheme.typography.titleMedium)
                            Text(formatDistance(place.distanceMetres.toDouble()) + " away", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            if (place.address.isNotBlank()) Text(place.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            TextButton(onClick = { keyboard?.hide(); actions.onDismissPlaces() }, modifier = Modifier.fillMaxWidth().heightIn(min = GuideSpace.control)) { Text("Change destination") }
            Text("Place and route data © OpenStreetMap contributors", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(GuideSpace.section))
    }
}

@Composable
private fun HelpDialog(onRemoteSetup: () -> Unit, onKey: (android.view.KeyEvent) -> Boolean, onSpeak: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, null) },
        title = { Text("Walking with GuideLight") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(GuideSpace.large)) {
                FilledTonalButton(onClick = onRemoteSetup, modifier = Modifier.fillMaxWidth().heightIn(min = GuideSpace.control)) {
                    Icon(Icons.Outlined.Bluetooth, null)
                    Spacer(Modifier.width(GuideSpace.small))
                    Text("GuideLight button setup")
                }
                RemoteDialogKeys(onKey)
                FilledTonalButton(onClick = onSpeak, modifier = Modifier.fillMaxWidth().heightIn(min = GuideSpace.control).testTag("help-speak")) { Text("Speak a command") }
                Text("Voice access\nSay Home navigation, open camera, back to navigation, GuideLight button setup, clear destination, or what can I say. Wispr needs the keyboard while recording; GuideLight closes it when dictation finishes.")
                Text("Speak a destination\nTap Speak a destination, then hold the Wispr Flow bubble. GuideLight searches when your words arrive.")
                Text("Choose a place by voice\nSuggestions are read aloud with numbers, distances and addresses. Use Wispr Flow to say choose suggestion 1, repeat suggestions, or change destination. Your GuideLight button works on this screen too.")
                Text("Keep your camera forward\nCamera warnings use sound and vibration. Repeat guidance reads the last warning and turn.")
                Text("Commands during a walk\nThe rear camera fills the screen, with the next direction over it. Open Walking controls in the left corner for the map and other actions. Use the GuideLight button or microphone with Wispr Flow. Say scan surroundings, repeat alert, show map, recenter, full route, camera view, location status, camera status, open controls, close controls, help, repeat directions or end walk. Saying end walk stops navigation immediately.")
                Text("Object descriptions\nLeft/right and high/low refer to the camera view. Small, medium and large describe apparent image size, not real dimensions or distance. Scanning can miss objects. No objects detected does not mean the path is clear.")
                Text("Home navigation\nThe saved-home trial records three connected rooms with a helper, then tests recognition after reopening without an Earth marker. Name rooms through Wispr and save the map. Walking directions are not active in this trial. Previous room recordings opens the older marker setup.")
                Text("Wispr Flow must be installed and set up separately. Its microphone and overlay permissions are managed in Wispr Flow.")
                Text("Named-place search shares your destination and location with Nominatim. Nearby category searches share your location and category with Overpass, with Nominatim as backup. Searches check 250 m, 500 m and 1 km first, expanding only when no suggestions are returned. Clinics, including homeopathic clinics, are separate from Chemist. Open-map listings can miss local businesses. Select a suggestion to start walking directions inside GuideLight. Walking routes use Valhalla, and map tiles use OpenFreeMap. Map data © OpenStreetMap contributors, © OpenMapTiles. Distances are approximate and listings do not confirm opening hours.")
                Text("GuideLight ${com.guidelight.app.BuildConfig.VERSION_NAME}")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } }
    )
}

@Composable
private fun RemoteSetup(state: GuideUiState, actions: GuideActions) {
    AlertDialog(onDismissRequest = { actions.onRemoteSetup(false) },
        icon = { Icon(Icons.Outlined.Bluetooth, null) }, title = { Text("GuideLight button") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(GuideSpace.large)) {
                RemoteDialogKeys(actions.onHardwareKey)
                FilledTonalButton(onClick = actions.onSpeak, modifier = Modifier.fillMaxWidth().heightIn(min = GuideSpace.control).testTag("setup-speak")) { Text("Speak a settings command") }
                Text("On Navigate or the walking camera, press the GuideLight button once. Wait for the vibration, speak with Wispr Flow, then press again to send.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Use GuideLight button", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = state.remoteEnabled, onCheckedChange = actions.onRemoteEnabled,
                        modifier = Modifier.semantics { contentDescription = "Use GuideLight button" })
                }
                Text(if (state.remote.connected) "Button control is running." else if (state.remotePermissionGranted) "Permission is saved. Waiting for Android to connect button control." else "Button control is not running. Open Accessibility settings to enable or restart GuideLight button control.", style = MaterialTheme.typography.bodyMedium)
                Text("This optional service finds and holds Wispr’s floating button. It does not record audio or upload screen content. It only operates while GuideLight is in front.", style = MaterialTheme.typography.bodyMedium)
                Text("Use short presses. Holding this remote can power it off. Sessions stop after 30 seconds. Wispr updates or screen touches can interrupt this experimental control.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = actions.onRemotePermission, modifier = Modifier.fillMaxWidth().heightIn(min = GuideSpace.control)) { Text("Accessibility settings") }
            }
        },
        confirmButton = { TextButton(onClick = { actions.onRemoteSetup(false) }) { Text("Done") } }
    )
}

@Composable
private fun CameraPlaceholder(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.CenterFocusStrong, "Camera preview", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
    }
}

fun formatDistance(metres: Double): String = if (metres >= 1_000) String.format(Locale.US, "%.1f km", metres / 1_000) else "${metres.toInt()} m"

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DestinationPreview() { GuideLightTheme { GuideLightApp(GuideUiState(locationReady = true), GuideActions()) } }

@Preview(showBackground = true, widthDp = 393, heightDp = 852, fontScale = 1.5f)
@Composable
private fun LargeTextPreview() { GuideLightTheme { GuideLightApp(GuideUiState(), GuideActions()) } }

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun WalkingPreview() { GuideLightTheme(darkTheme = true) { GuideLightApp(GuideUiState(routeActive = true, routeName = "India Gate", routeDistance = "3.0 km", routeDuration = "About 48 min", instruction = "Turn left onto Kartavya Path", direction = GuidanceDirection.LEFT), GuideActions()) } }
