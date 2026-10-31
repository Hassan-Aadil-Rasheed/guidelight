# Camera walking — 0.20.0

The walking view uses the rear camera across the whole screen. System-bar-safe
overlays hold a compact destination/distance row, the navigation arrow and next
instruction, the microphone and the Walking controls button in the upper left.
The map and secondary actions are opened from that corner instead of remaining
in a long scrolling page. Camera capture stays mounted behind these panels.
Text uses a light foreground on dark camera chrome. Walking-mode object markers
show only the corners of up to two possible path hazards; neutral detections do
not cover the preview with full rectangles.

## Touch and voice

Walking controls provides repeat directions, scan surroundings, repeat alert,
map, location status, camera status, camera view, help and end walk. The map
retains Recenter and Full route. Touching End walk asks for confirmation;
explicitly saying End walk retains the existing immediate-stop behaviour.

All walking tools have exact Wispr commands: **open controls**, **close controls**,
**repeat directions**, **scan surroundings**, **repeat alert**, **show map**,
**recenter**, **full route**, **camera view**, **location status**, **camera status**,
**help** and **end walk**. During confirmation, **yes** ends the walk and **keep
walking** cancels the confirmation.

The existing paired Selfie remote uses one short press to arm the persistent
command editor and hold Wispr's bubble, and another press to release/send.
“Scan the room”, “look around” and “what do you see” also request an object scan.
The editor stays mounted in the same activity while map/menu/help panels change.
It appears when input is armed, when confirmation is required, or on an error.
TTS remains muted during dictation. The microphone also arms this same editor.
The camera footer distinguishes a disconnected Selfie remote from an unavailable
button service. Reconnecting the remote is announced during a walk.

Touch controls use at least 56 dp targets. Instructions, panels and confirmation
can scroll when text is enlarged; the camera itself remains the background.
The camera view does not open an external maps application.

## Object descriptions

The existing local ML Kit detector processes camera frames, including ARCore
CPU images during a walk. Observations describe the object's centre as left,
ahead or right, and high, middle or low **in the camera view**. Small means less
than 7% of the visible image area, medium less than 25%, and large 25% or more.
These words describe image occupancy, not physical dimensions or distance.

Automatic descriptions require three stable observations, pause during Wispr
dictation and higher-priority speech, and are rate limited. Scan surroundings
reads up to three current distinct descriptions, with larger image regions first.
The visual caption shows only the largest current description to keep the camera
view clear.
The existing path-corridor caution/stop heuristic retains priority, sound and
vibration, and adds the same appearance description. Its visible banner expires
after eight seconds unless refreshed; Repeat alert retains the last alert.

Stale descriptions are discarded after two seconds. An empty detection is said
as “No objects detected in this view. Objects may be missed.” It never establishes
that a path is clear. A monocular camera cannot determine real object dimensions
from these boxes; detection can miss objects. Continue using a cane or guide dog.

The navigation arrow now has a separate heading cue. Turning around can point
backward while the ground ribbon remains suppressed when it cannot be projected
in front of the phone. Weak location/heading still suppresses untrusted guidance.

## Android references

- [Compose accessibility semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)
- [Accessible touch targets](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [Camera preview lifecycle](https://developer.android.com/media/camera/camerax/preview)

No new paid APIs, providers or camera uploads were added.

## Validation

- Version 0.20.0, Android version code 40; installed on the connected OPPO phone.
- 170 unit tests passed and Android lint reported no errors.
- All 21 initial selected phone checks passed. After the visual contrast and
  marker refinements, all 13 final selected checks passed, including camera
  coverage, stable Wispr editor, enlarged text, confirmation, native map recovery,
  six graphics checks, and the real named-place route/command flow.
- All five final targeted UI checks passed after the editor timing, connection
  status and natural-command refinements. These cover the camera/menu layout,
  persistent editor focus, button setup, and enlarged-text confirmation controls.
- The connected flow inserts Wispr-style text into the actual activity editor
  and checks camera/menu/map/help transitions. It does not synthesize human
  speech or a physical Bluetooth radio-button press.
- The separate live user check did verify actual Selfie presses, Wispr's hold and
  release, incoming spoken text, and a `SCAN` walking action. A disconnected remote
  and Android's stopped GuideLight accessibility service were recovered first.
  The walking editor now waits for visible layout before requesting focus and
  opening the keyboard; this resolved input-readiness failures in the live check.
- Verified final screenshots in `output/screenshots/camera-walking-live.png`
  and `output/screenshots/camera-walking-controls.png`.
- APK SHA-256: `E10934811F3BB71780EF0F1DDDD7A3139EA1E41CA7BB09ED60654A50196AC9B4`.
- Removed the temporary test package, restored the phone's original sleep setting,
  and rebound the existing GuideLight accessibility service after testing.

If the button stops starting Wispr, check that **Selfie** is connected in Bluetooth.
If the footer says the button service is unavailable, open Accessibility settings
and enable or restart **GuideLight button control**. An enabled setting alone does
not establish that Android has bound the service after updates or device tests.
