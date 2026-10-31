# Outdoor guidance — 0.12.0

The walking screen now speaks directions relative to the camera facing forward,
instead of asking the user to interpret compass names such as northwest.
Keep the phone upright at chest height, rear camera facing the walking direction.

- Start: “Turn left/right to face the route”, “Turn around to face the route”,
  or “Continue straight”. A missing/unreliable compass requests alignment rather
  than guessing a relative direction.
- Approach: distance along the route to the next maneuver, rounded to about five
  metres. Announcements occur at roughly 100, 50, 20 and 10 metres; the near-turn
  instruction says “Turn left/right now”. Prompts are rate-limited and repeat
  after thirty seconds; Repeat directions always reads the latest instruction.
- The screen displays remaining route distance. Arrival means near the mapped
  endpoint, not finding the actual door.
- Route progress uses the decoded Valhalla geometry and maneuver shape indices.
  Merely approaching within 35 metres no longer advances to the next instruction.
- Poor/old GPS, substantial route deviation, or an unreliable compass clears the
  camera route preview. Off-route travel asks the user to pause/check with help;
  automatic rerouting is not included.

## Blue camera guide

During an outdoor walk, SceneView/ARCore owns the camera. Horizontal plane
finding, current camera pose and camera projection replace the fixed screen beam.
The short route preview uses actual route geometry transformed into camera-relative
right/forward offsets. It bends at nearby mapped corners and moves with camera pose.
Only the part within a currently tracked plane polygon is drawn. Low surfaces
must be 1–2.2 metres below the camera, assuming chest-height use. A missing surface,
tracking loss, uncertain bearing, or an out-of-view route hides the guide.

The surface is an estimated horizontal plane, **not a road/sidewalk classifier**.
The guide is a local route-direction projection, not a surveyed pavement lane or
a persistent geographically anchored route. GPS and compass errors can place it
incorrectly. It cannot certify that a surface is clear, walkable, or safe to cross.
ARCore's plane polygon is not an obstacle-free map. No depth sensor is required
or assumed available. Slopes, steps, glare, low texture, darkness, and magnetic
interference can prevent or distort this preview.

ML Kit consumes the same ARCore camera images. Detection boxes intersecting the
projected ribbon use that polygon for the existing size-based warning heuristic;
when projection is unavailable, the conservative camera corridor still gives
generic object warnings. Outlines remain visible. This does not measure object
distance or guarantee small-object detection or automatic avoidance.

Outside an outdoor walk, the Camera tab continues using CameraX + MlKitAnalyzer.
Indoor Home navigation retains its separate recorded-path implementation and
camera-aligned overlay. Wispr input and the two-short-press Selfie control remain.
Spoken warnings/directions remain muted while GuideLight dictation is armed.

## Skills and references

Discovery used the skills.sh leaderboard and `skills find android` / `skills find
ARCore`. The already installed Find Skills, Google CameraX (including XR and
ML Kit coordinate guidance), and Mobile Android Design skills cover this work.
No additional skill or runtime package was needed: SceneView, ARCore, ML Kit and
Android sensor APIs were already dependencies. Low-install third-party XR search
results were not installed merely to increase the skill count.

- [Android position sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position)
- [Sensor coordinate system](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)
- [ARCore planes and polygon bounds](https://developers.google.com/ar/reference/java/com/google/ar/core/Plane)
- [Valhalla maneuver types and shape indices](https://valhalla.github.io/valhalla/api/route/api-reference/)

## Validation

The debug APK and test APK build successfully. All 91 unit tests pass and lint
has zero errors. Seven outdoor connected checks passed on the OPPO CPH2505:
five overlay drawing checks, actual CameraX streaming/rebinding, and live place
search/choice/routing with the new AR camera followed by Repeat directions and
immediate End walk. The AR check requires object analysis to be ready; it does
not require or establish real ground-plane alignment.

Three other Home checks passed (voice bridge coalescing, editor stability and
isolated storage). The Home camera/response test timed out waiting for speech
completion; audio-focus logs show repeated notification interruptions. A focused
retry failed at editor focus, and subsequent inspection found another app in
front. These observations do not establish a Home speech fix, nor prove a single
cause for the earlier audio failure. No real room was saved by these checks.

Physical route following and outdoor plane projection require an assisted walk;
simulated data and screen-render tests do not establish physical positioning or
detection accuracy. Continue using a cane or guide dog.

APK: `GuideLight-0.12.0.apk`, SHA-256
`55501CE701CEB70F9CF8C38A32F9C215897D82C66F4509E3897C3CA9B903779E`.

Final version/code `0.12.0/22` is installed and its device APK SHA-256 matches.
The temporary test package was removed. Reinstalling the final APK restored both
Wispr Flow and GuideLight button control as bound Accessibility services with no
binding/crashed entries. The temporary USB stay-awake setting was restored to its
original value (`0`), and GuideLight was opened on Navigate without an active walk.
