# Free nearby search — 0.19.0

**Current version 0.19.1:** the external Google Maps button and app-launching
code have been removed at the user's request. Nearby searches, result selection,
live maps and walking directions stay inside GuideLight. The close-area search
and Clinics category described below remain available. The Google Maps section
and validation below record the earlier 0.19.0 implementation.

GuideLight uses its existing free map/search services and offers an optional
link to the regular Google Maps app. No Google Maps Platform API key, billing
account or paid Maps/Places SDK is used.

## Close places first

Both the nearby service and its geocoder backup check 250 m, 500 m, 1 km,
2 km and finally 5 km. They stop at the first radius that returns suggestions.
A close result is retained rather than widening the search to fill five slots.
Suggestions remain ordered by approximate straight-line distance; the walking
route is calculated after selection.

The backup requests up to 40 candidates in each bounded area before selecting
up to five. Previously, it requested only eight candidates across a 5 km area,
which could miss closer businesses. Separate branches with the same business
name and address text now retain their separate coordinates, so a farther
record cannot remove a closer branch before distance sorting.

The public services remain rate limited, cached, cancellable and suitable for
this small prototype. Their listings are incomplete and do not establish which
business is physically closest or currently open.

## Clinics and homeopathy

Clinics is a separate nearby category, with doctor and homeopathic/homoeopathic
clinic phrases supported. A named query such as **Dr Azad Homoeopathic Clinic**
is kept intact. A clinic is not automatically classified as a chemist.

The user reports that clinic is about 350 m away. The regression test uses a
fictional clinic at a similar distance to check the search logic; this is not
evidence that the real clinic is present in OpenStreetMap.

## Find in Google Maps

Tap **Find in Google Maps** from the destination or suggestion screen. GuideLight
opens Google's regular Maps app, or its website when the app cannot handle the
link. Category searches use phrases such as “pharmacy near me”; a named clinic
keeps its name. GuideLight does not attach GPS coordinates to this link. Google
Maps uses its own location permissions and business listings.

Choose a result in Google Maps, then **Directions → Walking**. GuideLight's
camera pauses while another app is in front. Return to GuideLight to use its
in-app camera and walking features. This option does not import or scrape
Google's listings into GuideLight.

References: [Google Maps URLs—no API key](https://developers.google.com/maps/documentation/urls/get-started),
[bounded geocoder amenity search](https://nominatim.org/release-docs/latest/api/Search/#result-restriction),
[public geocoder policy](https://operations.osmfoundation.org/policies/nominatim/).

## Validation

- Version 0.19.0, Android version code 38.
- 164 unit tests passed; Android lint reported no errors.
- APK includes all 30 expected ARM64 native libraries.
- APK SHA-256: `B0F4DF71643A60B85B0DC9285DF5884F19B38DA5C180C24FAD48347DDC8A72FB`.
- Installed and verified version 0.19.0 on the connected OPPO phone.
- All seven selected device checks passed across runs: three UI checks, real shop
  search, chemist search and walking route, Google Maps handoff, and the existing
  named-place voice-selection/walking flow. The last flow initially timed out
  waiting for voice-selected routing and passed on its isolated retry.
- The live shop search returned a grocery about 372 m away. The free in-app
  pharmacy data still returned Krishna Medicos at about 2.2 km; this update does
  not claim the missing closer pharmacies have appeared in that dataset.
- The consumer Google Maps search displayed a Dr Azad Homoeopathic Clinic map
  label. The test verifies the app handoff; it does not verify the user's reported
  350 m walking distance or the clinic's listing in OpenStreetMap.
- Removed the temporary test package and restored the phone's original sleep
  setting. GuideLight and Wispr Flow accessibility services remain enabled.

## Version 0.19.1 validation — external lookup removed

- Removed the external Google Maps button, app-launch code, package visibility
  entry and related lookup tests. Updated help and no-results wording.
- 161 remaining unit tests passed; Android lint reported no errors.
- All three selected phone checks passed: nearby category buttons, selecting a
  displayed suggestion, and a real chemist search with an in-app walking route.
  UI checks confirm the Google Maps button is absent from both destination and
  suggestion screens.
- Installed version 0.19.1, Android version code 39, on the connected phone.
- APK SHA-256: `52C9EA66DC994C82ED7B5604A100291C5CD14C22241DFD5B1A74CF67686A1170`.
