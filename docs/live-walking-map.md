# Live walking map — 0.18.0

The walk screen displays the pedestrian route, destination, moving position,
estimated accuracy circle and camera-facing direction on a native street map.
Recenter resumes following; Full route fits the entire walk. Dragging the map
stops following until Recenter is pressed. The map and spoken navigation use
the same accepted location stream.

## Why the old screen said “Location is uncertain”

`NavigationSession` rejected fixes older than ten seconds or less accurate than
25 metres. That replaced the entire turn card with a large warning. Meanwhile,
the activity independently subscribed to GPS and network providers, accepting
whichever callback had the newest timestamp. A coarse network update could
replace a fresh GPS fix and unnecessarily trigger the warning. These code paths
explain how the message can occur; the screenshots do not establish the actual
accuracy or provider on that phone.

## Position tracking and recovery

- Google Play devices use Fused Location Provider with high accuracy, a desired
  one-second interval, no movement threshold and no batching. This allows location
  to improve while standing still. Devices without Play services, or where the
  fused request fails, use the framework GPS/network providers.
- A coarse callback cannot replace a precise fix that is still within the
  ten-second freshness window. Older, invalid and duplicate fixes are rejected.
  Fresh precise recovery is accepted immediately.
- Blue means a fresh fix within 25 metres. Amber means approximate. Grey means
  last known (older than ten seconds). The accuracy circle reflects the reported
  horizontal uncertainty; the dot is an estimate, not an exact pavement position.
- Weak location keeps the map, route and remaining distance visible. The compact
  message says turn guidance is paused; it removes the straight-ahead arrow and
  ground projection. It does not advance route progress or declare arrival.
- Location loss is announced once per episode rather than repeating every thirty
  seconds. Repeat directions still reads the current status. Guidance resumes
  after location and heading recover. Remaining walking time scales with progress.
- Settings opens app permissions when precise location is missing, otherwise
  phone Location settings. The app cannot guarantee a precise fix indoors.

## Mapping choices

MapLibre Native renders OpenFreeMap's Liberty map. No Google Maps API key or
billing setup is required. The existing Valhalla blind-profile pedestrian routes
and voice/camera controls remain in use. Map tiles require internet; map download
failure exposes Retry map and does not disable location or spoken instructions.
MapLibre's attribution control remains visible. Help describes OpenFreeMap tile
requests and map attribution.

The implementation does not fabricate movement using step counts, snap an
uncertain position to a sidewalk, or infer a safe crossing from map geometry.
Off-route recovery still asks the user to check their position; automatic
rerouting and background walking remain outside this version.

## References

- [Android continuous location updates](https://developer.android.com/develop/sensors-and-location/location/request-updates)
- [Fused Location Provider](https://developers.google.com/location-context/fused-location-provider)
- [Android precise and approximate permission](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime)
- [MapLibre Native Android quickstart](https://maplibre.org/maplibre-native/android/examples/getting-started/)
- [OpenFreeMap mobile styles](https://openfreemap.org/quick_start/)

## Phone verification

Validation on 29 October 2026: all 140 unit tests passed, Android lint passed,
and the APK/test APK built successfully. Three connected checks passed on the
OPPO CPH2505: native street-map loading and position recovery with Full route /
Recenter; controls reachable with 2× text; and actual destination search,
pedestrian routing, camera readiness, Repeat directions and End walk. The map
rendering check uses sample Delhi coordinates, not a physical walking trace.

Version 0.18.0 (code 36) is installed on the phone. The temporary test package
was removed, GuideLight was reopened on Navigate, and both GuideLight button
control and Wispr Flow remain enabled Accessibility services.

APK: `GuideLight-0.18.0.apk`, SHA-256
`3B989D48C5B9F8ADBD5E92AC793AC16DA4A6FB603E91C27A0B6915A9C6B377CD`.
Phone previews are in `output/screenshots/live-walking-map.png` and
`output/screenshots/weak-signal-walking-map.png`.

Use an assisted outdoor walk to check map following, GPS accuracy and spoken
turn timing. Test precise versus approximate permission, Location off/on, loss
of internet, Full route/Recenter, map dragging, leaving/reopening the app, and
large fonts/TalkBack. Map tests or simulated fixes do not validate physical
pedestrian positioning.
