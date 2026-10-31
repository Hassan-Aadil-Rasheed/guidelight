# Voice nearby searches and camera alerts — 0.21.0

The destination screen no longer has a Find nearby section or category buttons.
Use the Selfie remote and Wispr Flow, or Speak destination. A text entry remains
available as an accessible fallback. Places, choices and routes stay in GuideLight.
No paid Maps SDK or API key was added.

## Find everyday places

Say “Nearest masjid”, “Closest temple”, “Nearest church”, “Nearest public toilet”,
“Find a bus stop near me” or “Nearest chemist”. The app reads numbered choices.
Say “Choose the first one” to request its walking route. “Change destination”
returns to destination entry. Specific place names still use ordinary place search.

The 53 category types cover worship (temple, mandir, mosque/masjid, church,
gurdwara/gurudwara, synagogue, Buddhist temple and general worship); chemists,
clinics, hospitals, dentists, opticians and medical labs; toilets, drinking water,
bus stops/stations, metro, rail and taxi; banks, ATMs, police and post offices;
shops, groceries, supermarkets, restaurants, cafes, bakeries, fruit/vegetables,
dairies and markets; laundry, hairdressers/barbers, tailors, clothes, shoes,
hardware, mobile phones and electronics; parks, benches, libraries, community
centres, schools, colleges, universities, hotels, petrol and EV charging.

Other explicit requests such as “Nearest florist” use a bounded local text
search. This offers a fallback, rather than promising that every possible type
exists in the provider's index. Nearby searches require a recent phone location,
start in small areas, expand up to 5 km and sort matches by approximate straight
line distance. Walking routes can be longer. Religious categories use the mapped
religion, including in backup results, to avoid returning a different faith's
place of worship.

The existing approved free providers remain Overpass and Nominatim. Their
OpenStreetMap coverage can omit an actual nearby business. A lack of results
does not mean no such place exists. No result is fabricated or silently changed
to a worldwide place search. Requests are rate limited and cached.

## Hear the camera

“Scan surroundings” from destination entry opens Camera without touching a tab.
“Back to navigation” returns. The camera screen now also has the command editor
used by Selfie/Wispr. During a walk, “Look ahead”, “Repeat alert” and “Camera
status” continue to work.

Automatic object descriptions previously waited for a six-second gap after
navigation speech. Frequent navigation updates could prevent that gap, and could
interrupt a camera utterance. Camera announcements now have their own completion
tracking. Navigation resumes with the current instruction after the camera
utterance finishes; a timeout releases a failed speech callback. Stable central
objects are described before side objects. Repeated unchanged detections are
paced, rather than spoken on every frame.

The phone check also exposed a separate walking-camera failure: ML Kit's stream
mode repeatedly returned zero detections for the sampled ARCore images, while
CameraX detected objects in the same scene. The ARCore analyzer now uses complete
single-image detection for its throttled samples, with multiple objects enabled.
It retains frame-age checks, coordinate mapping and one image in flight. No
second camera session is opened alongside ARCore.

Warnings retain vibration and protected speech. During actual Wispr dictation,
speech stays muted to avoid dictating the app's own voice. A warning can resume
only while it is still fresh; stale warnings are discarded. An explicit scan can
wait briefly for a fresh camera frame. Backgrounding the app clears pending scans.

Positions and “appears small/large” refer to the camera image. The model does not
measure an object's real dimensions or distance and may miss objects. This is
still a secondary aid alongside a cane or guide dog.

## Validation

Validated on the connected OPPO CPH2505 with version code 42 / 0.21.0:

- Debug build and Android lint passed; all 190 unit tests passed.
- Both nearby UI checks passed, including absence of the category buttons.
- Real-location voice input returned mapped mosque, temple, church and public
  toilet suggestions. The test inserts the transcript Wispr would provide; it
  does not replace the live providers with sample results.
- The real walking-route check passed all walking commands and observed the
  Android TTS engine start and complete a camera announcement while route
  updates continued. After the detector change, ARCore samples returned multiple
  mapped objects in the previously failing scene.
- The voice-only scan/open-camera/return-to-navigation check passed on the final
  build. The user also confirmed hearing the spoken object alerts.
- The temporary test package was removed, GuideLight/Wispr accessibility services
  were restored, and the original plugged-in screen-sleep setting was restored.

The final APK is `GuideLight-0.21.0.apk`, SHA-256
`B635239DD592786857224BA0A8C7835493F03A8C741D43CF6D9405D880DBD4DE`.

## References

- [Nominatim bounded search and amenity queries](https://nominatim.org/release-docs/latest/api/Search/)
- [OpenStreetMap places of worship and religion tags](https://wiki.openstreetmap.org/wiki/Tag:amenity%3Dplace_of_worship)
- [OpenStreetMap public transport mapping](https://wiki.openstreetmap.org/wiki/Public_transport)
- [OpenStreetMap toilets](https://wiki.openstreetmap.org/wiki/Toilets)
- [ML Kit detection modes for Android](https://developers.google.com/ml-kit/vision/object-detection/android)
