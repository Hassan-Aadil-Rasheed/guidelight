# Home navigation — 0.12.1

This assisted indoor prototype saves named rooms and the paths physically
recorded between them. It supports the fixed 18 cm Earth marker tested in
0.10.0 and the exact additional Earth photo the user displayed on the laptop.
Each saved home retains its reference identity. It does not discover rooms, infer a floor plan, or invent a direct route
through walls. The marker must stay upright in the same physical position.

## First setup, with a sighted helper

1. Open **Navigate → Home navigation → Scan home marker**.
2. Scan the whole fixed Earth image in good light. Listen for **Marker found**.
3. Path learning starts automatically near the marker. Keep the phone upright,
   at chest height, with its rear camera facing where you walk. There is no
   separate Start/Finish recording step.
4. Walk the real usable path with your helper. Stop at the kitchen, then say
   **This is my kitchen** or **It's my kitchen**. Wait to hear **kitchen saved**.
5. Continue to other rooms, saying **This is my washroom**, **That's my bedroom**,
   etc. Naming a room saves both its location and the actual walked path.
6. Say **List my rooms** (or tap **Hear my rooms**) to hear saved names. Say
   **Take me to my kitchen** to navigate; no Finish command is needed. Unsaved
   path after the last named room can be used to walk back in the current session,
   but is discarded on tracking loss or closing. Saved room paths remain.

Use the same two short Selfie-button presses as outdoor navigation: press, wait
for vibration, speak, then press to submit. Holding the remote can power it off.
The on-screen Wispr action prepares an empty editable field for the normal Flow bubble.
GuideLight speech is muted during dictation; camera visuals/vibration continue.
After submission, consumed command text is cleared. A new complete phrase inserted
directly by Wispr is accepted as a new command; single-key typing stays manual so
a partly typed room name is not saved after a pause. For recording-time speech
muting, keep using the Selfie button or the on-screen Speak action before speaking.

## Walking to a saved room

- On every reopening or tracking loss, scan the fixed marker again. Rooms are
  stored in marker-relative, gravity-aligned coordinates, not a previous ARCore
  session's coordinate system.
- Say **List my rooms** to hear all saved rooms with numbers, then **Take me to the
  kitchen** or **Choose room one**. Plain saved room names are also recognized.
- A route can start on a recorded path in the same tracked session, including
  from one saved room to another. Away from the recorded path, navigation refuses
  to start; return with help. It never creates a new connection across open space.
- Directions use the phone's forward-facing camera orientation. Spoken left,
  right, ahead and turn-around cues follow recorded segments; remaining distance
  is approximate path length in metres. It is not cm/mm positioning.
- **Repeat directions** reads the current instruction and remaining distance.
  **End walk** stops immediately, without confirmation. Touch controls are also
  provided. Ending the walk keeps saved rooms and leaves the camera available.
- Tracking loss, stale camera frames, an unexpected pose jump or leaving the current recorded path
  stops active guidance. Saved rooms remain; rescan and explicitly choose a route
  again. A recording interrupted by tracking loss also stops; saved checkpoints
  remain, and its unsaved tail is discarded.
- Navigation waits for object scanning to be ready. If scanning becomes
  unavailable during a walk, guidance stops; choose the room again after checking
  with your helper.

## Camera warnings

ARCore owns the camera. The existing on-device ML Kit generic object detector
processes ARCore CPU images without starting a simultaneous CameraX session.
Sensor rotation is removed from detections and ARCore maps them into the actual
view crop. Detected objects have outlines; the existing camera-aligned cyan
corridor appears while walking. It is not a world-locked line on the floor.

Warnings use the existing screen-space size/corridor heuristic, not physical
depth. The OPPO's tested ARCore session reports depth unavailable. No bed/table
classification, exact object distance, small-object guarantee or automatic
detour is claimed. Low light, reflective/featureless surfaces and moving
furniture can defeat detection or tracking. This requires an assisted trial;
keep the usual cane or guide dog.

## Implementation and data

- SceneView/ARCore: rendering, image reference and visual-inertial pose tracking.
- ML Kit: generic object detection from ARCore's CPU camera images.
- Jetpack DataStore plus Kotlin Serialization: atomic local room/graph storage in
  `files/datastore/home-map.json`. Unreadable/invalid data is retained and recording
  is disabled; it is never silently replaced with an empty map.
- JGraphT Dijkstra routing: only recorded graph edges are traversable, in either
  direction. Nearby graph nodes are never merged merely by distance.
- One home, one floor, up to 20 uniquely named rooms and 3,000 path points. Existing
  room names cannot be overwritten accidentally. No delete/reset operation is
  exposed in this version.
- Only the foreground/resumed activity collects the Wispr bridge state, preventing
  the outdoor activity from cancelling an indoor dictation in the background.
- Camera images and transcripts are not logged or stored by this feature. Wispr
  remains a separately installed service and manages its own processing.

## Validation — 28 October 2026

### 0.12.1 Home camera and command replacement

- The user reported a black Home preview and repeated failure to recognize the
  Earth marker. Initial inspection found the ordinary Camera tab instead of Home;
  Home was opened explicitly. Home then showed a black preview and ARCore's
  insufficient-light status, also confirmed by the user. Later the phone left
  GuideLight, so an inactive camera dump is not evidence of a sensor fault.
- The Home AR SurfaceView previously lived at the bottom of a scrolling page.
  Opening the keyboard or scrolling could move it out of the visible window.
  It now stays in a fixed visible area above the command scroll. This addresses
  surface visibility; physical marker recognition still requires a real check.
- New Wispr sessions clear old text, and successfully consumed text is removed
  from the editor and IME surrounding text. Complete direct insertions can submit
  automatically. Single-key edits stay manual. Manual input no longer depends on
  the optional Bluetooth Accessibility bridge being connected; a real remote
  disconnect still cancels its own session.
- Setup object warnings are suppressed before home alignment, so the laptop used
  as the marker display does not repeatedly interrupt setup and list responses.
- A later live capture with the fixed preview showed a different Earth photograph
  on the laptop. The exact local `images.jpeg` was inspected and added unchanged
  as a second 18 cm reference (`home-earth-personal-18cm`). Both original and new
  images score 100 in arcoreimg's individual and combined-database suitability
  checks; this score is not measured recognition accuracy. The old single-image
  database remains for MarkerTestActivity; Home loads a two-image database.
- Maps and recorder snapshots retain the selected marker ID. A populated map
  only aligns against that saved reference, preventing silent switching between
  the two pictures. Legacy version-1 maps remain readable. The Home landing page
  shows both supported references for a new home and only the saved one later.
- The AR preview is kept above the scrolling controls and Home explicitly uses
  adjustResize, avoiding whole-window panning when Wispr opens the IME.
- Camera warning speech is reserved for active indoor walking. Recording keeps
  visual/haptic feedback without repeatedly cutting off setup/room responses.
  Audio-focus CAN_DUCK notification events no longer stop and restart Home speech;
  actual transient/full focus loss still pauses output and defers the latest state.
- Debug builds emit only AR camera ID, tracking state/reason, database size and
  frame counts. No image, transcript, room name or position is logged. Connected
  checks verify a two-image database and fresh camera frames while the editor/IME
  is active. Existing saved room data and coordinate transforms are retained.
- Skill discovery: Find Skills and Google's CameraX skill (XR, coordinate and test
  guidance) were used. The official SceneView skill was reviewed (vendor repo
  1,352 stars; two directory installs at lookup), but its examples target 4.x.
  The running app remains on 2.2.1 and uses that tag's actual ARSession/ARSceneView
  source instead of copying incompatible APIs. No new runtime package is required.
- The first 0.12.1 phone run passed both new command-reset cases, bridge submission,
  editor stability and isolated storage. AR camera 0 tracked the environment and
  produced fresh frames while the keyboard was open; the live preview was visible.
  The then-single-reference marker did not match the displayed alternate Earth.
  That run's speech-completion check still timed out during repeated notification
  interruptions, motivating the audio-focus and setup-warning changes above.
- Final build and all 92 unit tests passed; lint has zero errors. All six final
  connected Home checks passed: camera/database setup and fresh frames with the
  IME open, actual Home speech completion, coalesced bridge submission, two direct
  Wispr-style batches clearing/submitting independently without the optional bridge,
  single-key typing staying manual, editor stability and isolated storage. Some
  checks cover multiple assertions in one test. This validates inserted text,
  not a new physical Selfie/Wispr microphone trial.
- Physical recognition of the new reference, room saving and two consecutive
  spoken commands must be confirmed separately; no live room has been saved by
  automated tests. The app refuses to save a room before valid marker alignment.
- APK: `GuideLight-0.12.1.apk`, SHA-256
  `A3EB0015D1AA49538C56FE57CE46C8CF597C43B89064A79C70A5C50F9CAB6AEE`.
- The final APK was installed before the passing phone run. The test package was
  removed afterward, but USB disconnected during the usual final reinstall to
  restore GuideLight's Accessibility binding. After reconnection, the final reinstall
  succeeded and device SHA-256 matched the APK above. Wispr Flow and GuideLight
  button control are both bound, with no binding/crashed entries; the temporary test
  package is absent. Physical marker/room acceptance is still pending.

References: [ARCore augmented images](https://developers.google.com/ar/develop/java/augmented-images/guide),
[SceneView skill](https://github.com/sceneview/sceneview/blob/main/agents/sceneview/SKILL.md),
[version-matched AR session source](https://github.com/sceneview/sceneview/blob/v2.2.1/arsceneview/src/main/java/io/github/sceneview/ar/arcore/ArSession.kt).

### 0.11.2 speech delivery and marker reacquisition

- The user confirmed seeing the list response without hearing it, and reported
  the laptop marker was not recognized. A later camera capture showed the Earth
  image visibly in the preview while Home still requested alignment. Initial
  captures were dark or outside Home, so those alone did not explain the failure.
- A read-only debugger probe found the running Home speech engine ready and
  connected, with dictation disarmed. An earlier interactive breakpoint caused
  an app restart; debugger connections/port forwards were removed afterward.
- A foreground sound diagnostic synthesized a fixed phrase, found non-silent PCM
  (`71,210` WAV bytes, peak `16,677`), and completed both direct TTS and file
  playback without error. The user confirmed hearing the sound test. No user
  microphone audio was recorded; the temporary generated file was deleted.
- Home now explicitly uses navigation/speech audio attributes, requests and
  releases transient audio focus, and sets a normal per-utterance volume without
  changing the phone's volume setting. Speech failures/focus waits are visible.
- A response interrupted by engine initialization or foreground changes is
  retried from the latest UI state. Original queued warnings/turns are not kept.
  Speech remains muted during Home dictation and active Wispr bridge phases;
  arming clears pending delivery. Fresh foreground responses replace pending ones.
- Marker acquisition considers all currently tracked images in the AR session,
  not only a frame's update events, so a previously detected image can be
  reacquired. Tracking still requires full image tracking and a valid pose.
- The initial Home page shows the exact reference thumbnail. The exact bundled
  JPG was also opened on the laptop. Feature comparison of the earlier camera
  capture was inconclusive, so a different image was not asserted as the cause.
- Final APK built; all **81 unit tests passed**, including six deferred-speech
  cases. Lint has zero errors. Installed version/code `0.11.2/21` and the device
  APK SHA-256 matched the built APK despite an ambiguous installer return.
- The first final connected Home checks were interrupted by USB loss. The durable
  retry later returned three UI/focus failures and one passing storage check while
  the phone was locked. A later 0.12.0 run passed voice coalescing, editor stability
  and storage; its camera/list check timed out waiting for speech completion amid
  repeated audio-focus interruptions. A focused retry failed at editor focus and
  subsequent inspection found another app in front. Home audibility is not confirmed.
  Physical marker recognition and Home response audibility on 0.11.2 are still
  unverified. The earlier diagnostic sound success is not Home acceptance.
- APK: `GuideLight-0.11.2.apk`, SHA-256
  `FE54B430FCFD68B23FA5DE7C30F228A46BF36D38C20521DF6B4B3EA88C883294`.

Primary audio reference: [Android audio focus](https://developer.android.com/media/optimize/audio-focus).

### 0.11.1 simplification

- The user reported inserted words without an understandable result. On-device
  inspection showed **List kitchen** had reached the parser but was rejected;
  **It's my kitchen** was also unsupported by the original narrow save grammar.
- Natural save phrases (this/that/it is my room, contractions, remember room) and
  everyday list requests are now supported. Listing updates visible feedback as
  well as speech. An already submitted command shows **Command received** instead
  of a misleading Send action.
- Learning starts at marker acquisition automatically. Navigation can use the
  already walked draft tail to return to a saved room without another label or
  a separate Finish command. Only named checkpoints are committed to disk.
- A bridge transition straight from HOLDING to IDLE reschedules pending text, so a
  coalesced SUBMITTING state does not strand a command. Timeout cancellation calls
  the input controller explicitly. Speech initialization retries the current
  response through the dictation mute gate.
- All 75 unit tests passed; lint has zero errors. Four connected checks passed
  on the OPPO: bridge HOLDING→IDLE pending-text submission, Home camera/ML Kit
  restart and natural list commands, editor text/focus stability, and isolated
  DataStore recreation/routing. The Home command check also verifies the actual
  TTS engine starts playback after the list response; this does not establish
  audibility at the user's Bluetooth device.
- No live save-room command is injected by the phone tests, since a physical
  marker could be visible and cause an unintended real room to be saved. Room
  labels, path commits and navigation along an uncommitted walked tail are
  tested against isolated fixtures instead.
- Physical automatic learning and “It's my kitchen” through Selfie/Wispr still
  need the user's assisted home trial.
- Subsequent user trial still reported words appearing without a response.
  This remains an unresolved physical-input/audio issue despite the automated
  checks. On inspection the phone was locked and the previous Home activity had
  closed, so its failed input state was unavailable. Home was reopened for a
  stationary “List my rooms” reproduction; no further code fix has been inferred
  without capturing that failure. No real home-map file had been committed.
- A later inspection found OPPO's separate Camera app in front, launched from
  the phone launcher; GuideLight Home was paused. The phone subsequently locked
  due to its timeout. Selfie was paired but absent from the active input/HID list.
  These observations are environmental state, not proof of the original failure.
- After the next unlock, the live GuideLight Home screen showed **No rooms saved
  yet. Scan the home picture...**, confirming a list action produced its expected
  on-screen response. Home was not marker-aligned. Audio was routed to the phone
  speaker at 4/16-equivalent media volume. User confirmation of audibility is
  pending; no further code change was inferred from this screen alone.
- APK: `GuideLight-0.11.1.apk`, SHA-256
  `8C58340E30CFEACE8F4ADA638BAD76596FBD4E4718F1DCA41DAAA2D4B0C891F6`.
- Final APK reinstalled and version verified on the phone after removing the test
  package. Wispr and GuideLight button control are bound, with no binding/crashed
  services. Home navigation was selected for the user trial; USB disconnected
  before another foreground-window check. The installed app can be tested
  without USB.

### 0.11.0 baseline

- Version 0.11.0 (code 19) debug APK and test APK built; lint has zero errors.
- All 71 unit tests passed. New coverage includes command parsing and negation,
  saving checkpoints without mutating previous snapshots, duplicate-name and
  floor/jump guards, graph routes around corners without invented edges,
  reverse routing, off-path stopping, left/right instructions, path distance,
  JSON round trips and invalid-data retention, marker-frame alignment across
  changed AR origins, and rotation/crop mapping for object boxes.
- Five checks passed on the USB-connected OPPO CPH2505:
  - Isolated DataStore file survives store recreation and routes correctly on
    Android. The real home file was not modified.
  - The Wispr editor retains text and focus across recording/walking UI changes;
    the touch End walk callback needs no confirmation.
  - ARCore and ML Kit camera sharing, unknown-room rejection, simulated text
    insertion, stopping and restarting the Home camera.
  - Real outdoor destination search, numbered choice, pedestrian route, repeat
    directions and immediate spoken End walk.
  - Existing CameraX streaming and restart across Navigate/Camera tabs.
- The temporary test package was removed and the final APK reinstalled. Version
  0.11.0 was verified on the phone. Wispr Flow and GuideLight button control are
  bound, with no binding/crashed services. The normal Home landing screen was
  visually inspected on the phone; real room data was not changed by automation.
- Physical Selfie-to-Wispr commands in the new Home activity and assisted room
  recording/navigation remain to be tried by the user. Automated text insertion
  does not establish actual speech recognition or Bluetooth reliability.
- Real home alignment after reopening, route-following accuracy and obstacle
  detection reliability require physical trials. Earlier successful marker
  tests do not validate indoor navigation safety or measured distance accuracy.

APK: `GuideLight-0.11.0.apk`, SHA-256
`93CD163F0FCDB3B9AD2B7DBDF18B8E9393EF983B87AD8FB279F13AD43F928917`.

Primary references:
[ARCore image tracking](https://developers.google.com/ar/reference/java/com/google/ar/core/AugmentedImage),
[ARCore ML input](https://developers.google.com/ar/develop/java/machine-learning),
[view coordinates](https://developers.google.com/ar/reference/java/com/google/ar/core/Coordinates2d),
[ML Kit](https://developers.google.com/ml-kit/vision/object-detection/android),
[DataStore](https://developer.android.com/topic/libraries/architecture/datastore),
[JGraphT](https://jgrapht.org/javadoc/org.jgrapht.core/org/jgrapht/alg/shortestpath/DijkstraShortestPath.html).
