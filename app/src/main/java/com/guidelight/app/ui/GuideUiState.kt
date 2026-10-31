package com.guidelight.app.ui

import com.guidelight.app.domain.GuidanceDirection
import com.guidelight.app.domain.ObstacleRisk
import com.guidelight.app.navigation.PlaceResult
import com.guidelight.app.navigation.NearbyCategory
import com.guidelight.app.navigation.GeoPoint
import com.guidelight.app.navigation.WalkingPosition
import com.guidelight.app.remote.BridgeStatus

enum class WalkingPanel { CAMERA, CONTROLS, MAP, HELP }

data class GuideUiState(
    val destination: String = "",
    val busy: Boolean = false,
    val status: String = "",
    val error: String? = null,
    val voiceArmed: Boolean = false,
    val voiceKeyboardHidden: Boolean = false,
    val voiceRequest: Int = 0,
    val cameraAllowed: Boolean = false,
    val cameraReady: Boolean = false,
    val cameraStatus: String = "Starting camera…",
    val locationReady: Boolean = false,
    val preciseLocationAllowed: Boolean = true,
    val places: List<PlaceResult>? = null,
    val nearbyCategory: NearbyCategory? = null,
    val nearbyLabel: String? = null,
    val choiceInput: String = "",
    val routeActive: Boolean = false,
    val walkCommandInput: String = "",
    val confirmEndWalk: Boolean = false,
    val walkingPanel: WalkingPanel = WalkingPanel.CAMERA,
    val mapOverview: Boolean = false,
    val mapCameraRequest: Int = 0,
    val sceneSummary: String? = null,
    val primaryObjectDescription: String? = null,
    val screenRequest: Int = 0,
    val requestedScreen: String = "navigate",
    val routeName: String = "",
    val routeDistance: String = "",
    val routeDuration: String = "",
    val remainingDistance: String = "",
    val routeShape: List<GeoPoint> = emptyList(),
    val walkingPosition: WalkingPosition? = null,
    val turnGuidanceAvailable: Boolean = true,
    val relativeRouteBearing: Double? = null,
    val navigationArrowBearing: Double? = null,
    val groundPreview: List<Pair<Float, Float>> = emptyList(),
    val instruction: String = "",
    val direction: GuidanceDirection = GuidanceDirection.STRAIGHT,
    val arrived: Boolean = false,
    val lastWarning: String? = null,
    val alertVisible: Boolean = true,
    val warningRisk: ObstacleRisk = ObstacleRisk.NONE,
    val helpVisible: Boolean = false,
    val remoteEnabled: Boolean = false,
    val remotePermissionGranted: Boolean = false,
    val selfieConnected: Boolean = false,
    val remoteSetupVisible: Boolean = false,
    val remote: BridgeStatus = BridgeStatus()
)

data class GuideActions(
    val onHardwareKey: (android.view.KeyEvent) -> Boolean = { false },
    val onIndoorTest: () -> Unit = {},
    val onDestination: (String) -> Unit = {},
    val onSpeak: () -> Unit = {},
    val onSearch: () -> Unit = {},
    val onChoosePlace: (PlaceResult) -> Unit = {},
    val onDismissPlaces: () -> Unit = {},
    val onChoiceInput: (String) -> Unit = {},
    val onSubmitChoice: () -> Unit = {},
    val onRepeatPlaces: () -> Unit = {},
    val onRepeat: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onStopConfirmation: (Boolean) -> Unit = {},
    val onWalkCommand: (String) -> Unit = {},
    val onWalkAction: (com.guidelight.app.domain.WalkVoiceAction) -> Unit = {},
    val onSubmitWalkCommand: () -> Unit = {},
    val onHelp: (Boolean) -> Unit = {},
    val onCameraPermission: () -> Unit = {},
    val onLocationSettings: () -> Unit = {},
    val onRemoteSetup: (Boolean) -> Unit = {},
    val onRemoteEnabled: (Boolean) -> Unit = {},
    val onRemotePermission: () -> Unit = {},
    val onDestinationScreenReady: (Boolean) -> Unit = {}
)
