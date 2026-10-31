# GuideLight button: setup and investigation

## Audible connection name — 0.22.3

Live testing of 0.22.2 confirmed that both physical presses reached Wispr and
completed submission. The user reported that the new name was not heard.
Connection announcements had been limited to an active outdoor walk, and the
normal startup greeting did not name the remote.

Version 0.22.3 says “GuideLight button connected” at startup when the enabled
remote is present, and after a connection change on the main screen, including
destination entry. The latest connection change waits while dictation is active
or urgent camera speech is running. Superseded events, disabled controls and
backgrounded activities drop pending cues. Connection speech is queued behind
ordinary speech and never deliberately speaks over Wispr recording.

Validation for 0.22.3: build and Android lint passed, with all 207 unit tests
passing. A dedicated connected-phone test required the real button to be present
and observed Android TTS start and complete the new-name announcement on the
main screen. Earlier live checks of the renamed setup UI, settings dictation and
private bridge connection all passed, and real physical presses completed the
Wispr start/submit flow. The temporary tests were removed; both accessibility
services were restored and the original screen-sleep setting was restored.
The user then confirmed hearing the new name when the normal app opened.

Installed version code 47 / 0.22.3 matches the exported APK
`GuideLight-0.22.3.apk`, SHA-256
`3111F4A090AF695CA695F6F8CCE3456F1383B9C678D09F467F7184FC6928EE58`.

## Remote name — 0.22.2

The remote is called **GuideLight button** in setup, Help, accessibility
descriptions, connection messages, dictation prompts and Home navigation. Press
once to speak with Wispr Flow and again to send, as before. Voice controls include
“GuideLight button setup”, “enable GuideLight button”, “disable GuideLight
button” and “how do I use the GuideLight button”. “Guide light button” transcripts
are also understood.

The input-device filter recognises `GuideLight button` and `GuideLight`, ignoring
case and extra whitespace. It also accepts the exact factory HID name `Selfie`
because Android can continue reporting that name after a Bluetooth alias is
changed. Factory-name compatibility is internal; it is never used as the spoken
display name. Unrelated input-device names and the phone's volume keys remain
excluded. Existing opt-in preferences, accessibility permission and two-press
behaviour are retained.

The sections below record earlier versions and the original hardware name.

Initial version 0.22.2 validation: debug and test APKs compiled, Android lint passed and
all 204 unit tests passed. The new checks cover renamed/factory input-device
names, exclusion of unrelated devices, polite new-name commands and negation.
The existing setup UI tests were updated to the new accessible labels. Connected
phone checks and installation initially could not run because no phone was detected over
ADB; the subsequent live results are recorded above. No remote opt-in preferences were changed.
Export: `GuideLight-0.22.2.apk`, SHA-256
`78D180BEE3851840FC58FD86F6A30FE503687F022F66978E486A5301C384F1F9`.

## Two-press interaction — 0.8.0

The two-short-press bridge now works on both destination entry and the numbered suggestions screen. After GuideLight reads the results, use Wispr Flow to say “Choose suggestion two”, “Repeat suggestions” or “Change destination”. Choice text is separate from the destination. See [spoken suggestions](voice-suggestions.md) for implementation and validation details. The user confirmed that the 0.7.2 empty-recording retry works.

### Empty-recording recovery — 0.7.2

Version 0.7.2 fixes recovery after an empty dictation. In 0.7.1, SUBMITTING consumed every new press until text arrived or the 25-second timeout expired. Now a new short press during SUBMITTING clears the old destination/debounce and starts a fresh bridge session after the previous pointer-up completes. The new session invalidates the old no-text timeout. The waiting message explicitly offers another press to retry. This bridge cannot identify late vendor text by recording ID because Wispr inserts ordinary editable text.

Validation: 24 unit tests and 8 emulator UI checks passed; the build and static checks completed with no errors. Version 0.7.2 (code 11) was installed and opened on the OPPO; both Accessibility services remain enabled. During the requested physical test, logs showed SUBMITTING at 01:59:20.536 followed by a fresh PREPARING at 01:59:22.052 and HOLDING at 01:59:23.080. Several subsequent submission/retry cycles also succeeded without waiting 25 seconds. The final submission at 01:59:33.256 returned to IDLE at 01:59:34.864 via text reception. These observations verify immediate bridge retry on this phone; they do not independently identify the vendor's microphone state or the user's speech content.

### Previous physical validation — 0.7.1

On 28 October the user confirmed that Wispr starts recording, but reported that holding the selfie remote powers it off and release does not stop dictation. Device logs showed the initial continuous-gesture extension being cancelled after entering the held phase. The user explicitly selected two short presses instead of physical hold-and-release.

Version 0.7.1 uses one short press to start and another to stop/submit. Physical key-up only finishes the click. The logical recording session retains its input-device identity after that key-up, so a later Bluetooth disconnection or app backgrounding still ends the owned screen contact and disarms automatic search.

The gesture adapter now sends one pointer-down stroke marked to continue, keeps that pointer down without sending redundant stationary gestures, and sends one final pointer-up continuation. Minimum long-press readiness is measured with monotonic real time rather than assuming a stationary gesture callback waits for its declared duration. The pending stop, interruption and 30-second cap all use this real-time policy.

Version 0.7.1 build validation: 24 unit tests and 8 emulator UI checks passed; static checks passed with no errors. Phone installation completed successfully after the unlock/approval step, and version code 10 was verified. Both Accessibility services remained enabled. After the user was asked to reconnect Selfie and test two short presses, repeated physical-phone sessions logged completed pointer-down and pointer-up events followed by SUBMITTING and IDLE, without the earlier continuation cancellation. A phone screenshot showed the transcribed destination “the India Gate” in GuideLight. This verifies observed text delivery and successful stop-gesture dispatch; it does not independently measure Wispr microphone state or establish reliability across interruptions.

The same screenshot showed a walking-route error. A separate public sample route in Delhi returned HTTP 200 and a valid pedestrian route using the app's request options. The phone disconnected before its specific route failure could be diagnosed. Route creation and walking guidance were not verified by this remote-button test.

## Initial prototype implementation — 0.7.0 (historical)

An opt-in Android AccessibilityService is implemented. Open Help → Selfie button setup, turn on Use selfie button, then enable GuideLight button control in Android Accessibility settings. Wispr Flow must retain its own Accessibility, overlay and microphone permissions.

- Only the paired input device named Selfie and its Volume Up key are intercepted, through the foreground window's public `Window.Callback`. Phone volume keys and unrelated remotes are untouched. No Bluetooth scan/connect permissions or global key-filtering capability are requested.
- Only the idle Navigate screen accepts a new hold. A pressed key focuses the normal destination field; the service then waits up to 3 seconds for one small Wispr edge overlay.
- The target is selected from live Android accessibility-window identity and bounds, not fixed coordinates. No target, off-screen targets, full-width transparent overlays and multiple possible targets are rejected.
- Android `GestureDescription.StrokeDescription` continuation keeps the same synthetic pointer down. The first contact exceeds the long-press threshold; following chunks are 120 ms. Release sends a continuation with pointer-up rather than another tap. A tap shorter than the initial preparation period starts no gesture; release just after contact may produce an empty dictation rather than hands-free recording.
- A short vibration marks the held-bubble phase; a longer vibration marks release/submission. The UI labels this as holding the Flow button, not independently verified microphone recording.
- Maximum hold is 30 seconds. No transcript after 25 seconds shows an error. Repeated key-down events cannot start duplicate sessions. Device removal and backgrounding release the held control and disarm automatic destination search.
- The app stays awake while its remote option is enabled and GuideLight is visible. It does not unlock the phone or run this control from other apps or the lock screen.
- The bridge has no microphone capture, transcript storage, clipboard access or network client. Existing destination search still uses its documented map providers after Wispr inserts text.

This is UI automation of a third-party control. Wispr updates, screen magnification/touch exploration, keyboard/window changes, interruptions and lost gesture callbacks may prevent it from working. If a gesture is interrupted or rejected, the app reports an error and tells the user to check Wispr; it cannot independently guarantee the vendor microphone has stopped. Keep this optional until physically verified.

Build validation on 27 October 2026: debug APK built, 20 unit tests passed, static checks passed with no errors, and 8 emulator UI tests passed, including explicit remote opt-in and Accessibility setup visibility. Version 0.7.0 installed successfully on the OPPO. USB disconnected during setup navigation. User enablement and the physical remote → Wispr recording → release → inserted destination check remain pending; no end-to-end success is claimed.

Framework references: [AccessibilityService.dispatchGesture](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,%20android.accessibilityservice.AccessibilityService.GestureResultCallback,%20android.os.Handler)), [continued strokes](https://developer.android.com/reference/android/accessibilityservice/GestureDescription.StrokeDescription#continueStroke(android.graphics.Path,%20long,%20long,%20boolean)).

Requested interaction: hold a handheld selfie shutter button to start Wispr Flow recording, release to submit, then receive the destination in GuideLight.

## Verified

- GuideLight already focuses a normal editable destination field and accepts text inserted by the Wispr Flow bubble. After 1.2 seconds without further changes, it parses the destination and searches for places.
- Wispr's Android documentation supports pressing and holding the floating bubble, then releasing to submit: [first dictation](https://docs.wisprflow.ai/articles/5906198129-starting-your-first-dictation-on-android).
- Android supports hardware `KeyEvent` down/up callbacks and repeated down events during a held key: [keyboard actions](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/commands).

## Unverified / blocked

- The remote reports the input-device name `Selfie`. Its input capabilities include `KEY_VOLUMEUP`, `KEY_VOLUMEDOWN`, `KEY_MUTE` and `KEY_POWER`. A deliberate physical hold was captured: `KEY_VOLUMEUP DOWN` at monotonic time 22543.617178, then `KEY_VOLUMEUP UP` at 22545.690813 — a 2.073635-second hold. The user also confirmed that the phone showed its volume-up slider. This remote does expose distinct down and up events suitable for detecting a held button.
- No documented Android hardware hotkey or third-party start/stop integration for Wispr Flow was found in the official Android help pages researched on 27 October 2026. This does not establish that every private/vendor integration is impossible.
- The OPPO CPH2505 was reconnected and GuideLight 0.6.0 (version code 8) installed successfully. Wispr Flow 2.8.3 (version code 182) is installed. Its exposed deep-link filters cover authentication, opening the app and LinkedIn connection; they do not advertise recording start/stop actions. No undocumented deep links were invoked. The selective remote-event capture was stopped after verification; Bluetooth identifiers were not stored in this document.

## Next device checks

1. Completed: reconnect the OPPO and identify the Selfie input device.
2. Completed: capture and verify a real Volume Up hold/release. A separate short-click mapping is not yet verified.
3. The user approved the experimental accessibility bridge. Implementation is present; physical validation of target discovery, recording, release, text insertion and interruption handling is still required after the user enables its separate permission.
4. If supported, wire the remote to the documented start/stop control, suppress repeat-triggered sessions, and announce recording/submitting/error states. Test lost connection, release, cancellation and delayed transcription.
5. If no supported control exists, report that limitation before choosing a different speech engine or a separate user-approved automation bridge. Do not silently replace Wispr Flow, implement a fake recording indicator, or ship a fixed-coordinate bubble tap as a reliable integration.

Bluetooth microphone support and Bluetooth button activation are different capabilities. Wispr's support for the active headset microphone does not imply remote shutter support.
