# GuideLight UI refresh — 0.6.0

## Design direction

GuideLight helps blind and low-vision people choose an outdoor destination and follow spoken walking instructions. The destination screen has one job: choose where to go. Camera scanning is available from its own tab and starts automatically during a walk.

The signature is the large speech action, with its microphone and Wispr Flow label, followed by a quieter destination field. White surfaces on a cool blue background separate controls from camera imagery. Route guidance uses a large directional symbol with readable instructions. Blue, white and slate form the everyday palette; amber and red are reserved for caution and stop alerts. Text and icons also convey their meaning. Setup details and prototype explanations live in Help rather than repeating on every screen.

- Palette: background `#F4F7FC`, text `#13243D`, primary `#2252B8`, slate `#516078`, warning `#805100`, danger `#AD2525`.
- Typography: system sans serif; bold headings and semibold action labels, regular body text. System fonts keep accessibility scaling, offline use and startup reliable.
- Spacing: shared 4/8/12/16/24 dp scale. Primary controls are at least 60 dp high; icon controls at least 48 dp.
- Light and dark colour schemes are fixed so wallpaper colours cannot change the meaning of alerts.
- System bars and the keyboard use Scaffold safe-drawing insets. Content scrolls at larger font sizes.
- Route distance is explicitly labelled as route length, not remaining distance. Nearby obstacles have no fabricated metric distance.

## Research and skills

Installed and read:

| Skill | Source | Purpose |
| --- | --- | --- |
| mobile-android-design | [wshobson/agents](https://github.com/wshobson/agents/tree/main/plugins/ui-design/skills/mobile-android-design) | Material 3 components, Compose, accessible controls |
| migrate-xml-views-to-jetpack-compose | [android/skills](https://github.com/android/skills/tree/main/jetpack-compose/migration/migrate-xml-views-to-jetpack-compose) | Compiler setup, themes, interoperability, previews, UI tests |
| edge-to-edge | [android/skills](https://github.com/android/skills/tree/main/system/edge-to-edge) | System bars, keyboard and safe insets |
| camerax | [android/skills](https://github.com/android/skills/tree/main/camera/camerax) | LifecycleCameraController, MlKitAnalyzer and preview coordinate mapping |

The mobile design skill listed 25.1K installs and 41.8K repository stars at research time. Google's Android skills repository listed about 9.9K stars. Existing frontend-design, design-system and find-skills skills were also used. No unrelated web frameworks, experimental Compose Styles API or paid service was added.

The user's explicit redesign request supersedes migration-only visual-parity guidance. Existing theme values were intentionally replaced. The existing API 35 / AGP 8.7 / Kotlin 2.0 toolchain is retained with compatible stable library versions; adopting the newest Compose releases would also require a separate SDK and build-toolchain migration.

Official references:

- [Android accessibility](https://developer.android.com/guide/topics/ui/accessibility/apps): touch targets, contrast and labels.
- [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics): headings, roles and merged controls.
- [ML Kit Analyzer](https://developer.android.com/media/camera/camerax/mlkitanalyzer): library-managed image/preview transformations.
- [Compose dependencies](https://developer.android.com/develop/ui/compose/bom/bom-mapping).
- [Material Components maintenance mode](https://github.com/material-components/material-components-android): Compose is the actively developed Android UI direction.

## Package decisions

| Concern | Implementation |
| --- | --- |
| Buttons, fields, sheets, alerts, progress, navigation bar | Jetpack Compose Material 3 |
| Navigation and system Back | AndroidX Navigation Compose and BackHandler |
| Icons | Material Icons library |
| Camera preview and lifecycle | CameraX LifecycleCameraController / PreviewView |
| ML image analysis and coordinate mapping | CameraX MlKitAnalyzer + existing ML Kit object detector |
| HTTP requests | Shared OkHttp client with connection pool, timeouts and cancellation |
| Asynchronous work | Kotlin coroutines, lifecycleScope and Mutex rate limiting |
| Speech and vibration | Existing Android TextToSpeech and VibrationEffect |
| Place search and outdoor routing | Existing Nominatim and Valhalla services |

Application-specific destination parsing, navigation session logic and the illustrative corridor remain application code. A generic package cannot supply reliable indoor localization or physical obstacle depth from the existing camera heuristic. The corridor continues to be a visual aid rather than an AR-registered safe path.

## Verification

Compose instrumentation coverage includes destination entry/clear, disabled search, voice focus, exact place selection, end-walk confirmation, camera/help navigation, and 200% font scaling. Screenshots from light/dark destination, walking, camera, and large-text fixtures are written by instrumentation to the app's external files directory under `ui-review/`.

Existing domain tests continue to cover destination parsing and obstacle heuristics. Real walking, TalkBack usability with blind participants, physical obstacle performance and Wispr Flow dictation on the OPPO require device testing; emulator UI checks cannot establish those capabilities.

Validation on 27 October 2026: 10 domain unit tests and 7 Compose UI checks passed; live device-integration checks also passed for an India Gate place search and walking route, and camera streaming across tab switches. Lint passed with no errors. These checks validate application behaviour, not obstacle-detection accuracy. No precision/recall, missed-hazard rate or depth-error benchmark has been performed on the OPPO.
