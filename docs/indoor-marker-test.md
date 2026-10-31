# Indoor reference-image prototype — 0.10.0

Navigate → **Test indoor setup** opens a separate ARCore camera activity. Outdoor
CameraX detection is not mounted there. This is a tracking experiment, not room
navigation: no rooms, routes, obstacle distances, or floor maps are saved yet.
Wispr/Selfie voice control remains available on Navigate and during outdoor walks;
the new experiment has Start, Stop, and Read status aloud buttons.

## Physical test

1. Print `output/pdf/GuideLight-indoor-marker.pdf` on A4 at **100% / Actual size**.
   Disable Fit to page. Measure the full square image: **18 × 18 cm**, including
   its black background. Colour or grayscale is okay; don't crop or stretch it.
2. Fix it flat to a well-lit wall near the entrance. Leave it in that exact spot.
   For a first recognition check, a stationary laptop display can show the same
   square at 18 cm wide. Screen reflections may make this harder than paper.
3. Start camera test. Initially keep the full image prominent in the camera view
   (roughly a quarter of the frame or more). Wait to hear **Marker found**.
4. Slowly shift the phone 30–50 cm, with a helper and without needing to walk.
   **Distance from scan position** should change. This is the estimated straight
   displacement of the phone, not distance travelled or distance to the picture.
5. Cover the camera until tracking is lost. The number must disappear. Point at
   the picture again; a new scan starts the distance at zero.
6. Stop and restart, then close/reopen the app: it must ask for a new scan and must
   never reuse the previous session's number. The fixed picture is how later room
   coordinates could be aligned again; this test does not persist a room map.

Good recognition alone does not establish navigation accuracy. Drift, changing
lighting, featureless walls, reflective surfaces, camera position and moving the
marker all affect tracking. Test with assistance and continue using the user's
usual mobility aid. There are no obstacle alerts on this experiment screen.

## Implementation

- SceneView `arsceneview:2.2.1` supplies camera preview, rendering and AR session
  lifecycle; ARCore's Augmented Images database recognizes the fixed picture.
- The ARCore manifest requirement is optional so ordinary app features still run
  on devices without ARCore. SceneView checks installation and camera permission.
- Image must have `TRACKING` plus `FULL_TRACKING` before creating an anchor.
  Camera or anchor tracking loss detaches the anchor and clears measurements.
- Pause also clears the reference; resuming requires recognition again. A known
  image is retained in app assets, not a persistent ARCore world map.
- Anchor-relative camera positions provide displacement. Values are approximate,
  displayed in metres with one decimal; there is no cm/mm accuracy claim.
- Depth capability is checked at runtime but depth acquisition is not enabled.
  There is no Cloud Anchor, Geospatial request, paid mapping service, or upload of
  camera frames by GuideLight in this feature. ARCore's Google privacy disclosure
  and privacy-policy link are visible before starting.
- Logs contain lifecycle/capability events, no camera frames, room names or poses.

## Rebuilding the reference

The unmodified Earth image is from Google's ARCore Android SDK Augmented Images
sample (`samples/augmented_image_java/app/src/main/assets/default.jpg`), licensed
under Apache 2.0; licence retained in `assets/indoor/ARCore-sample-LICENSE.txt`.

ARCore `arcoreimg` reports image suitability **100/100**. This evaluates visual
features, not tracking/obstacle accuracy. Database entry:

```
home-earth-18cm|<absolute path to home-marker.jpg>|0.18
```

Use `arcoreimg build-db --input_image_list_path=... --output_db_path=...`, then
`python scripts/create-indoor-marker.py`. Keep the physical width consistent in
both the database and PDF.

Primary references: [Augmented Images guide](https://developers.google.com/ar/develop/java/augmented-images/guide),
[image quality](https://developers.google.com/ar/develop/augmented-images),
[SceneView 2.2.1 source](https://github.com/sceneview/sceneview-android/tree/v2.2.1),
[ARCore privacy requirements](https://developers.google.com/ar/develop/privacy-requirements).

## Validation — 28 October 2026

- Debug APK and instrumented-test APK compiled. 54 unit tests passed, including
  five for missing/invalid measurements, displacement, clearing after loss, and a
  fresh baseline after rescan.
- Final lint completed with zero errors.
- `arcoreimg eval-img` and `eval-db` both return 100 for the bundled reference.
  PDF rendered and visually inspected; one A4 page, verified 180 mm image width.
- Final 0.10.0 APK installed successfully on the OPPO CPH2505 after reconnecting
  USB. It pauses the camera synchronously before asynchronous session closure.
- Two connected checks **passed** on this phone: the AR camera produces frames,
  stops and restarts without displaying stale distance; the existing CameraX
  camera also streams and restarts across Navigate/Camera tabs afterward.
- After restoring the production APK, both Wispr Flow and GuideLight button
  control are bound, with no binding or crashed services. The test package was
  removed. The normal Navigate → Test indoor setup entry was opened successfully.
- In the live test the OPPO reports **Depth not available** for the active ARCore
  session. This runtime result overrides the earlier supported-device assumption;
  the marker experiment does not require depth. Initially the camera reported
  insufficient light.
- The user then confirmed hearing **Marker found** in the physical test on this
  phone. Reference-image recognition is confirmed by the user's report; print
  size, recognition range and detection latency were not measured.
- The user confirmed both additional physical checks **passed**:
  - Slowly moving the phone sideways roughly 30 cm increases the displayed
    displacement; returning to the scan position brings it near zero.
  - Covering the camera until tracking is lost clears the old distance;
    uncovering and rescanning recognizes the marker and restarts near zero.
- These results confirm the basic recognition/movement/reset workflow on this
  OPPO. They are user-reported qualitative checks, not measured accuracy results.
  Long-distance drift, alignment after reopening, different lighting and room
  navigation remain unverified. No rooms or connecting paths are saved yet.

Final APK: `GuideLight-0.10.0.apk`, SHA-256
`3E3626C61746594A262F40DA9A28AF7D3E1928AB665F5BD4FFD90C8101E7A66B`.
