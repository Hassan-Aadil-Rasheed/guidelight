# Saved-home recognition trial — 0.13.2

This is the first qualification milestone for indoor navigation from a saved
home map. It records a real RTAB-Map visual map, saves it locally, and tries to
recognize the map in a later camera session. No Earth marker is involved.
Room-to-room walking directions and obstacle avoidance are not enabled in this
trial. A successful match is not authorization to start walking independently.

## Assisted trial

1. Open Navigate → Home navigation → Create home map. With a sighted helper,
   slowly visit three connected rooms, including the hallways/doorways between
   them. Keep the camera upright and look at stable room details from different
   angles. Stop in each room, look around, then use Wispr to say “This is my
   kitchen”, “This is my hall”, “This is my bedroom”, or the actual names. Multiple
   views of the same named room can be marked. At least ten map views and three
   distinct room names are required before Save.
2. Say “Save map” or tap Save home map. Keep GuideLight open until the save
   completes. Camera images/features and room references stay in app-private
   storage on the phone. Old marker-based rooms and earlier saved visual maps
   are kept separately; a new recording never overwrites the previous map.
3. Close Home. With the helper, move to another mapped room, then reopen Home.
   Find my position loads the saved map into a fresh native session; reopening
   Home can start this automatically. Slowly look around at the views recorded
   earlier. Listen for “Saved home recognized” or “Recognized a saved view near
   kitchen”. Check the room with the helper. A generic map match does not claim
   that a particular room has been identified. Record the actual result; a failed
   recognition must not be treated as successful positioning.

During recognition, say “Where am I?” or “Which room am I in?” through Wispr.
The answer reports current matching/tracking status even when the session is
already running. A saved room list alone is not evidence of current position.

The Selfie two-short-press bridge and ordinary Wispr insertion use the existing
Home input controller. Supported controls include start mapping, save map,
find my position, list my rooms, repeat, and end test. End walk also ends this
test immediately. The previous marker prototype is available under Previous
room recordings.

## Engine and data

- RTAB-Map 0.24.2 is the actual native mapping/localization engine. A Java ARCore
  1.43 session supplies its external camera driver (3) with stride-packed NV21
  images, visual-inertial poses, intrinsics and sparse tracked 3D features.
  CameraX and the old Home AR session are not started here. A depth sensor and
  paid/cloud mapping service are not assumed. The live preview remains visible
  while ARCore is finding room details; tracking status explains light/motion issues.
- The unmodified arm64 native libraries come from the official project's
  published `introlab3it/rtabmap:android30` build. Only its Android build layer
  was fetched, digest-checked and read as a tar archive; no container is run and
  no filesystem image is installed. APK version metadata reports 0.24.2.
- Source artifact SHA-256:
  `405cf095747f27c5e1890f6c9787f0ca4b420bb2bd7daea749897dc3859bccca`.
  Layer SHA-256:
  `2cd3d36e86d5d7e6c7610db3ec792897f9744e6bbcb5da8a559f62e5e6f6860a`.
- ARCore's C/JNI shim libraries remain the existing Gradle 1.43 dependencies;
  the upstream artifact's older shims are not copied. Upstream Huawei provider
  libraries and OpenMP are loader dependencies of its native engine, but the
  selected camera source is always ARCore. This build targets this OPPO's arm64,
  4 KiB-page environment; other ABIs/page sizes are not qualified.
- JNI callback descriptors are checked against the upstream 0.24.2 source. The
  engine callbacks are not replaced with simulated matches. Java/Kotlin handles
  the app lifecycle, storage, voice commands and a conservative recognition gate.
- Home maps are RTAB-Map SQLite databases in `files/saved-home`. Map selection
  uses an atomic pointer. A completed map must pass SQLite quick_check and have
  at least ten nodes. The native engine is closed before the completed database
  is renamed/committed. Recognition uses a temporary copy so its session cannot
  update the stored home map.
- Room names are associated with recorded keyframes using capture timestamps.
  Native CameraMobile converts camera stamps to epoch time. After saving and
  closing the engine, each spoken room observation is attached to the closest
  stored Node stamp, within fifteen seconds. Conflicting room names on one node
  reject the save. The JSON sidecar records node-to-room references. Version
  0.13.2 resolves references on discarded nodes (negative native weight) to a
  retained view only within five seconds, 25 cm of the original recorded pose,
  and the same map component. Conflicting labels remain unidentified. Existing
  references are resolved in memory; saved databases and sidecars are not edited.
  Recognition still names only an exact retained reference, not the nearest room.
- Recognition requires three separated native visual matches with at least thirty
  inliers/matches, sufficient hypothesis score, no rejected hypothesis, and
  consistent corrected camera positions. Duplicate render draws do not satisfy
  the separation interval. Evidence expires after three seconds, and pausing
  resets the gate. Uncertain matching stays in the looking state.
- Maps contain private camera imagery. No map/image upload, cloud hosting,
  external messaging or location transmission is implemented by this feature.

## Acceptance and next milestone

Version 0.13.2 passes 106 unit tests, lint and the updated on-device SQLite
fixture test (save, recreate, and preserve the previous map on failed saves).
The user confirmed actual Selfie/Wispr commands, room recording and map saving.
The saved database has 157 nodes, including 91 retained nodes, and 12,602 visual
words. Seven of eight original room references pointed to discarded nodes;
all eight now resolve to retained nodes under the strict limits above. The stored
map's SHA-256 remained unchanged during the fix and reinstall.

“Where am I?” previously returned silently if a recognition session existed.
It now speaks the live status. Save controls are parsed before room labels so
“Save map” cannot create a room named map. Recognition initializes working memory
from all saved nodes and does not assume the previous shutdown position. The
fresh-match gate also clears a previous fix after an inconsistent pose jump.
The installed APK SHA-256 is
`daae52e59d1628262e69687d123d7cc6f34bc80e42f59fb41f59758caaa8515b`.
Physical recognition of the correct saved room after reopening is still pending;
the observed session had no accepted match and later reported insufficient light.

Historical checks below document the camera and voice integration stages.

Version 0.13.1 keeps voice controls above scrolling instructions, opens a standard
Android dialog with an EditText for Wispr input, restores listening/submission
vibrations, and speaks input failure messages. The dialog forwards Selfie keys to
the same controller and does not pause the mapping Activity. This follows a
device report that Selfie presses reached Home but Wispr kept its bubble hidden
despite the focused Compose editor and visible keyboard. An embedded native
editor was also absent from the accessible view tree. The separate dialog avoids
that interop path. The final 0.13.1 build passes 97 unit tests and lint and was
installed on the OPPO (APK SHA-256
`27733a5044076d70326b4366e8f73369445c540b4124d3502b8580063dde6c50`).
The dialog exposes a focused `android.widget.EditText` with the expected resource
ID, and Wispr's overlay was observed visible and touchable. A controlled keyboard
insertion of “List my rooms” was consumed, cleared, and answered “No rooms are
marked yet.” This is command-path verification, not real speech verification.
The user subsequently confirmed that two-short-press Selfie/Wispr recording of
“List my rooms” both recorded and received GuideLight's answer in Home. This
confirms the actual speech command path on this phone. Avoid
`uiautomator dump` during the physical voice check: repeated GuideRemote service
reconnections were observed during those inspections.

The initial 0.13.0 build passed 97 unit tests and lint. The on-device SQLite fixture
check passed. Initial native-camera and Java-camera automated ingestion checks
failed (zero recorded nodes); those failures are retained in the research logs.
After the independent live-preview correction, a manual OPPO check observed
1,604 ARCore camera frames, 97 submitted tracked frames and ten genuine RTAB-Map
nodes. It then correctly reported insufficient light rather than recording more
untracked views. The user confirmed that the room is visible in Home's preview.
A fresh mapping session then reported camera tracking ready and increased from
two to eight real nodes. Initial automated ingestion failures remain in the logs.
The later user map recording succeeded; recognition after a genuine restart
remains required. Unit tests cannot establish recognition accuracy.

Only after the assisted relocation trial succeeds should saved-map room routing,
walkable-space boundaries, pose/doorway error checks, and live obstacle scanning
be integrated. Sparse map points are not a complete obstacle-free floor plan.

Primary sources:
[RTAB-Map Android build](https://github.com/introlab/rtabmap/wiki/Installation#rtab-map-arcore-apk),
[native camera driver](https://github.com/introlab/rtabmap/blob/0.24.2/app/android/jni/CameraARCore.cpp),
[native app/JNI](https://github.com/introlab/rtabmap/tree/0.24.2/app/android/jni).
