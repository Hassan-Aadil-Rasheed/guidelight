# Nearby places — 0.18.1

This page records version 0.18.1. See [Free nearby search — 0.19.0](free-nearby-search.md)
for the search radii and Clinics category. Version 0.19.1 removes the external
Google Maps lookup and keeps nearby navigation inside GuideLight.

Navigate now offers Chemist, Shops, Groceries, Food, ATM and Hospital buttons.
Typed destinations and Wispr text such as “Nearest chemist”, “pharmacy near me”,
“Take me to the nearest medical store” and “shops near my location” use the same
category search. Named destinations such as Apollo Pharmacy or India Gate still
use the existing place-name search.

Nearby suggestions show up to five mapped places in approximate distance order.
They are read aloud with numbers and can be selected by touch or by saying
“Choose suggestion one”. Choosing a place builds the existing in-app pedestrian
route, with the live map and spoken turn instructions. Repeat directions and
End walk remain available.

## Location and search behaviour

- Nearby searches wait for a location fix no more than 30 seconds old.
- Category queries start within 1.5 km, expanding to 5 km when there are fewer
  than five results. Results outside the radius are discarded.
- A labelled node and building polygon for the same nearby business are
  deduplicated. Unnamed businesses retain a category label and real coordinates.
- Distances in suggestions are straight-line estimates. Walking distance is
  calculated after selection; the first result is not guaranteed to have the
  shortest walking route.
- A wider-search failure keeps any valid local results. Incomplete provider
  responses are errors, not evidence that there are no nearby places.
- If Overpass fails or returns no options, the existing Nominatim geocoder uses
  its documented small-area amenity search to return a limited shortlist within
  5 km. This is a selection of matches, not a complete directory. The Shops and
  Groceries backup searches convenience stores and supermarkets; Food searches
  restaurants and cafes.
- No-result text explains that no places are **mapped** within 5 km, without
  claiming that no businesses exist. Listings do not confirm opening hours.

## Provider and privacy

The user approved sending GPS coordinates and the selected category to the
public Overpass API, and approved Nominatim receiving the local map area and
category when used as a backup. Each provider also receives the network
connection's IP address. Requests contain a category filter and coordinates;
camera frames and voice recordings are not part of these requests. Help
describes the provider and location sharing.

Requests are user-triggered, serialized, cancellable, bounded to 5 km and spaced
at least 1.05 seconds apart per service. Nominatim backup requests share the
same rate limiter as named-place searches. Overpass requests have a 12-second
deadline; after a provider failure it is skipped for five minutes while the
backup remains available. Completed results are cached for five minutes for
the same category and location rounded to five decimal places. Public community
servers can be busy. A production deployment needs a contracted or self-hosted
provider.

- [Overpass around-coordinate queries](https://dev.overpass-api.de/overpass-doc/en/full_data/polygon.html)
- [Overpass public-service limits](https://dev.overpass-api.de/overpass-doc/en/preface/commons.html)
- [Nominatim bounded amenity queries](https://nominatim.org/release-docs/latest/api/Search/#result-restriction)
- [Existing Nominatim geocoder policy](https://operations.osmfoundation.org/policies/nominatim/)

## Validation

On 29 October 2026, all 157 unit tests passed and Android lint reported zero
errors. Five checks passed on the OPPO CPH2505: category shortcut callbacks,
nearby result selection, live Shops search, live “Nearest chemist” search and
walking-route selection, and the original named-destination route with Repeat
directions and End walk. The live checks exercise the approved backup during
Overpass availability problems. They do not establish complete business coverage,
opening hours or physical walking accuracy.

Version 0.18.1 (code 37) is installed. The temporary test package was removed,
the original USB stay-awake value was restored to `0`, and GuideLight was reopened
on Navigate. GuideLight button control and Wispr Flow remain enabled Accessibility
services. Previews are under `output/screenshots/nearby-*.png`.

APK: `GuideLight-0.18.1.apk`, SHA-256
`52BFB269095A6B07CB130E9F8A5B47693BE7A5FC557CEF3076F7A10793B638FF`.
