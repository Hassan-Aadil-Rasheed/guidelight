# GuideLight

GuideLight is an accessibility-first Android MVP for blind and low-vision pedestrians. It combines a voice destination command, in-app pedestrian directions, a rear-camera path scan, spoken alerts, and vibration cues.

> **Safety:** This prototype is an experimental secondary aid. It must not replace a cane, guide dog, orientation and mobility training, or normal road-safety judgment. The current vision model does not measure real-world distance and has not been safety validated.

## What works in this MVP

- Version 0.22.3 speaks “GuideLight button connected” when an enabled remote is
  present at startup or connects on the main screen. Connection changes wait
  until Wispr dictation and urgent camera speech finish.

- Version 0.22.2 calls the remote **GuideLight button** throughout spoken
  messages, setup, Help and Home navigation. Commands include “GuideLight button
  setup” and “enable GuideLight button”. Renamed remotes and the factory HID name
  are recognised by the same device filter. See [button setup](docs/bluetooth-button.md).

- Version 0.21.1 uses an invisible 3×3 camera grid for automatic object alerts.
  The top-centre, centre and bottom-centre cells are active; side objects remain
  available through “Scan surroundings”. Stable warnings repeat less often.
  See [camera focus and alert pacing](docs/camera-focus-grid.md).

- Version 0.21.0 removes the nearby category buttons and supports voice searches
  for 53 daily-place categories, including temples, masjids, churches, toilets,
  drinking water and transport. Camera speech can finish before a navigation
  update replaces it. Say “Scan surroundings” from the destination screen to
  open the camera by voice. See [voice nearby searches and camera alerts](docs/voice-nearby-alerts.md).

- Version 0.20.1 adds natural command phrases, polite requests, ordinal place
  choices and unique-name selection across walking, nearby search and existing
  home tools. See [voice command examples](docs/voice-command-phrases.md).

- Version 0.20.0 puts the walking camera across the screen, with a compact
  navigation overlay and a left-corner controls menu. GuideLight button/Wispr commands now
  cover the map, camera, scanning, alerts and other walking tools. Object alerts
  describe position and apparent image size. See [camera walking](docs/camera-walking.md).

- Version 0.19.1 keeps nearby searches and walking directions inside GuideLight.
  The external Google Maps button has been removed. Nearby lookup remains free,
  searches close areas first, and keeps Clinics separate from Chemist.
  See [free nearby search](docs/free-nearby-search.md).
- Version 0.18.1 introduced nearby category lookup (its shortcut buttons were
  removed in 0.21.0). Say “Nearest chemist” or “shops near me” to hear nearby
  choices, then select one for the walking map and spoken route. See
  [nearby places and provider details](docs/nearby-places.md).
- Version 0.18.0 adds a live walking street map with route, position, accuracy
  circle, direction, Full route and Recenter. Fused tracking prevents a coarse
  network fix from replacing fresh GPS; signal loss keeps the map visible with
  a compact recovery state. See [live walking map](docs/live-walking-map.md).
- Dedicated saved-room image comparisons for **Identify room**, with explicit
  no-match and ambiguity responses. See [room identification](docs/room-identification.md).
- Optional Cloudflare vision setup accepts a personal Workers AI token on the
  phone and tests a generated image before enabling cloud descriptions. See
  [Cloudflare setup and limitations](docs/cloudflare-setup.md).
- Home has on-demand local LFM3 vision descriptions and saved-room visual
  comparisons, plus text explanations of tracking status. Say “Describe the
  view”, “Identify this room” or “Explain my status” through Wispr. This does
  not enable indoor walking guidance. See [local AI setup and validation](docs/local-ai.md).
- Material 3 light/dark UI with separate Navigate and Camera tabs, large controls and scalable text.
- One large **Speak a destination** action with Wispr Flow guidance.
- A standard destination field that accepts text inserted by the Wispr Flow Android bubble.
- Automatic nearby-place search after Flow inserts a stable destination transcript.
- Commands such as “Navigate to Central Park” or a plain place name.
- Spoken, numbered suggestions for up to five matches, including distances and short addresses.
- Wispr commands such as “Choose suggestion two”, “Repeat suggestions” and “Change destination”, with GuideLight-button support on the choice screen.
- Location-biased search that can still find named places outside the nearby area.
- In-app pedestrian directions using Valhalla's blind walking profile; Google Maps is not opened.
- GPS-triggered spoken turn instructions while the camera remains active.
- Wispr commands during a walk: “Repeat directions” and “End walk”, using the GuideLight button or the on-screen voice control. Saying “End walk” stops navigation immediately.
- GuideLight speech pauses during Wispr input so cautions are not dictated as commands; detection, on-screen warnings and vibration continue.
- An outdoor cyan route preview projected onto tracked ground, with relative
  left/right speech and approximate distance countdowns. See [outdoor guidance](docs/outdoor-guidance.md).
- Detected-object outlines in both Camera and walking views, including when no route is active. Amber/red outlines mark possible path hazards; other detections use a neutral outline.
- Live CameraX preview and on-device ML Kit object detection.
- Screen-space obstacle warnings after three consecutive risky frames, including left/centre/right position and stop/caution urgency; no physical distance is claimed.
- Spoken warnings through Android Text-to-Speech and distinct vibration patterns.
- No API key. Location permission and internet are required for nearby search and routing.
- **Home navigation** now opens a saved-home recognition trial using RTAB-Map
  and ARCore, without an Earth marker. Camera ingestion and recognition on the
  OPPO are still being qualified; full saved-map walking directions are not yet
  available. See [saved-home trial](docs/saved-home-recognition.md).
- The previous Home prototype is under **Previous room recordings**: scan a fixed 18 cm Earth reference, record real
  paths with a helper, and name rooms through Wispr. Saved rooms persist locally;
  routes follow recorded paths with approximate metre estimates and generic object
  warnings. Setup and limitations: `docs/home-navigation.md`. The original marker
  experiment is documented in `docs/indoor-marker-test.md`.
  Version 0.12.1 supports both the original Earth reference and the user's exact
  alternate Earth photo, keeps the AR preview visible with the keyboard open,
  and clears each consumed Wispr command before accepting another.

## Run it

The debug build has been compiled and tested successfully. The generated APK is at `app/build/outputs/apk/debug/app-debug.apk`.

1. Install JDK 17 and Android SDK 35, or open this folder in the current stable Android Studio.
2. Build and test with `gradlew.bat testDebugUnitTest assembleDebug` on Windows.

For a fresh GitHub checkout, let Android Studio generate `local.properties` for
your SDK location. Local caches, diagnostics, APK exports and signing keys are
excluded from Git. Required ARM64 native libraries and their provenance/notices
are included. To update the existing development phone from another computer,
keep its original signing key locally at `migration/debug.keystore`; never commit
that key. A fresh checkout without it uses Android's normal local debug key.
3. Run on a physical Android 8.0+ phone. A physical phone is required for meaningful camera and walking tests.
4. For Wispr dictation, use Android 13+, install Wispr Flow, finish its onboarding, and enable its microphone, accessibility, and display-over-other-apps permissions.
5. In GuideLight, tap **Speak a destination** so the keyboard and Flow bubble appear. Dictate a place, choose the intended result, and keep GuideLight in the foreground for walking instructions and obstacle scanning. The Camera tab also scans without starting a route.

The project uses Kotlin, Jetpack Compose Material 3, Navigation Compose, CameraX, ML Kit, OkHttp and coroutines. It targets Android 15 (API 35) and supports Android 8.0 (API 26) and later. See [UI research and package decisions](docs/ui-redesign.md).

Version 0.8.0 uses an optional experimental GuideLight button bridge for both destinations and numbered place choices. Open Help → GuideLight button setup to opt in and enable GuideLight button control in Android Accessibility settings. With Navigate visible, press the paired GuideLight button once, speak after the vibration, then press again to submit. If you submitted without speaking, press again to start a fresh attempt. Use short presses: the user reported that holding this remote powers it off. The bridge operates the actual Wispr bubble; no Wispr API or MCP is used. Physical-phone testing of 0.7.1 observed successful stop-gesture completion and destination text arriving in GuideLight; the user confirmed empty-recording retries work in 0.7.2. Interruption reliability and independent microphone-state detection remain unverified. See [Bluetooth setup and limitations](docs/bluetooth-button.md) and [spoken suggestions](docs/voice-suggestions.md).

## Architecture

Version 0.9.2 fixes speech leaking into Wispr recordings and restores detected-object outlines without an active route. The user confirmed both fixes on the OPPO. Validation: 49 unit tests and six focused phone checks passed.

Version 0.9.1 adds Wispr controls during an active walk, including spoken end confirmation. It is installed on the OPPO; 42 unit tests and 15 connected phone checks passed. See [walking voice commands](docs/walk-voice-commands.md) for usage and physical speech-validation status.

Version 0.8.1 improves the choice-input handoff: it preserves the same editor across search/results, waits for a focused field and settled Wispr bubble, and allows manual retry while awaiting an empty transcript. On 28 October 2026 the user confirmed that spoken numbered suggestions and Wispr voice selection work on the OPPO. Interruption reliability remains unverified.

```text
Wispr Flow bubble ──> destination field ──> stable-text delay
                                                │
                       DestinationParser ──> nearby place choices
                                                │
                                 pedestrian route + GPS turns

Rear camera ──> CameraX ──> ML Kit detections ──> ObstacleEvaluator
                                                        │
                                            TTS + vibration warning
```

- Wispr Flow works through Android text insertion, so GuideLight receives ordinary text and needs no Wispr API or MCP connection.
- `domain/DestinationParser.kt` converts a transcript into a destination.
- `navigation/PlaceSearchService.kt` finds nearby OpenStreetMap places.
- `navigation/PedestrianRoutingService.kt` requests blind-profile pedestrian directions.
- `navigation/NavigationSession.kt` triggers upcoming maneuvers from live GPS updates.
- `vision/CameraObstacleMonitor.kt` owns the camera and ML Kit lifecycle.
- `domain/ObstacleEvaluator.kt` contains the testable screen-space risk heuristic.

## Important limitations

- **Prototype map services.** The public Nominatim and Valhalla services are appropriate only for a small MVP. A production deployment needs self-hosted or contracted providers, monitoring, caching, and published privacy terms.
- **Outdoor guidance.** Version 0.12 speaks relative left/right directions with approximate distance countdowns, checks GPS/heading freshness and detects substantial route deviation. Rerouting and offline route caching are not included. See [outdoor guidance](docs/outdoor-guidance.md).
- **The cyan outdoor guide is a local ground projection.** It uses ARCore tracked plane polygons and route geometry; it disappears when projection is unavailable. GPS/compass uncertainty prevents precise pavement alignment. It does not recognize safe roads, sidewalks or crossings. The separate indoor overlay remains camera-aligned.
- **No exact depth measurement yet.** A large box low in the image may be near, but this is currently a screen-space heuristic. The OPPO Reno8 T supports ARCore Depth; replacing the CameraX pipeline with ARCore Depth is the next required step for metre estimates.
- **Generic detection.** ML Kit's base detector detects prominent objects but does not reliably classify traffic hazards such as cars, curbs, potholes, or branches.
- **Wispr is external and online.** Its bubble requires Android 13+, a signed-in Wispr account, internet access, and Wispr's overlay/accessibility permissions.
- **No background operation.** The app scans on the Camera tab or during an active walk while visible.

## Verification checklist

- Grant camera access; confirm the live preview and “Path scan active” state.
- With TalkBack enabled, traverse every control and confirm labels are meaningful.
- Grant precise location, tap **Dictate with Wispr Flow**, say “I want to go to the park,” and confirm nearby choices appear instead of Google Maps.
- Choose a result and confirm a blind-profile walking route starts while the camera remains visible.
- Deny camera access; confirm voice navigation still works and Settings can be opened from the explanation.
- Put a large object in the center/lower camera view for several frames; confirm warning speech and vibration are rate limited.
- Test with airplane mode after installing an offline English speech pack.

See [docs/MVP_PLAN.md](docs/MVP_PLAN.md) for the delivery plan and acceptance criteria.

## Wispr MCP clarification

The MCP connection shown in Wispr Flow settings is not a live dictation interface. Wispr's own settings state that it lets AI tools access **Notetaker meeting notes and transcripts** and that it **does not have access to dictations**. It therefore cannot receive live phone microphone audio or transcribe a spoken destination for this app.

GuideLight uses the Wispr Flow bubble's normal Android text insertion. The Wispr MCP should only be considered later if the product adds a separate feature for searching past meeting notes; it must not be placed in the navigation-critical path.

## Technical references

- [Set up Wispr Flow on Android](https://docs.wisprflow.ai/articles/8858845757-setup-wispr-flow-on-android-android-settings)
- [ARCore supported devices](https://developers.google.com/ar/devices)
- [ARCore Depth API](https://developers.google.com/ar/develop/depth)
- [ML Kit object detection on Android](https://developers.google.com/ml-kit/vision/object-detection/android)
- [CameraX overview](https://developer.android.com/media/camera/camerax)
- [Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/)
- [Valhalla route API](https://valhalla.github.io/valhalla/api/route/api-reference/)

