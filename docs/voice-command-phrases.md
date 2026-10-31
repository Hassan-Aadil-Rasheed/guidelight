# Easier voice commands — 0.20.1

Use the existing selfie button or Wispr Flow microphone. Press the selfie button
once, wait for Wispr, speak, then press again to send. The same actions now accept
many everyday English phrases. Polite openings such as “could you please” and
endings such as “for me” work for controls; case, repeated spaces, punctuation
and common contractions are normalized.

## During a walk

| Action | Examples you can say |
| --- | --- |
| Scan objects | Look around; look ahead; what's in front of me; describe my surroundings; scan the room; check for obstacles |
| Repeat directions | Say that again; which way do I go; what's the next turn; read the directions |
| Repeat object alert | Read the last warning; what was the alert; repeat caution |
| Open map | Bring up the map; show me the map; switch to map; route map |
| Return to camera | Back to the camera; show live view; show rear camera; return to camera |
| Recenter map | Follow my position; center the map; find me on the map; keep the map on me |
| View full route | Show the whole journey; route overview; show the entire walk; fit the route |
| Location, distance and time | Where am I; how much further; time remaining; are we there yet; check GPS |
| Camera status | Is the camera working; are you scanning; is the scanner working |
| Open controls | Open the options; show me the controls; walking controls; bring up the menu |
| Close a panel | Dismiss the menu; go back; close the map; back to the walk |
| Help | What can I say; what commands are available; read the commands; how does the button work |
| End navigation | I'm done walking; finish my walk; stop navigation; cancel this walk; end the route |

Explicit end-walk phrases stop navigation immediately, as before. A bare **stop**
does not end an outdoor walk. **Yes**, **okay**, **go ahead** and similar replies
end a walk only while end confirmation is showing. **No**, **not now**, **keep
going** or **don't stop navigation** then cancel the confirmation.

Object size and position remain camera-view descriptions. Adding phrases does
not add depth measurement, object guarantees, map data or new navigation features.

## Destinations and nearby places

Say “could you guide me to India Gate”, “how can I reach Nehru Place”, “show me
walking directions to Central Park”, or just the place name. Recognized request
wrappers are removed while the destination's name and case are retained. Ordinary
names containing “to”, such as “Gateway to India”, are no longer split at that word.

Nearby terms include chemist/pharmacy/medical store/drugstore; shops/stores;
groceries/kirana/supermarket/convenience store; restaurant/eatery/dhaba/café;
ATM/cash machine/cash point; hospitals; and clinic/doctor/health centre/homeopathic
clinic. Common spellings such as “chemest”, “restraunt” and “resturant” are supported.
Try “could you find me the closest medical shop near here”. Named businesses and
explicit cities, such as “Apollo Pharmacy” or “chemist in Mumbai”, remain name searches.

## Choosing a displayed place

Say **first one**, **the second place**, **go with the second option**, **I'll take
the first one**, **option number two**, **use suggestion 3**, or **take the last one**.
Only the current displayed list is used. A unique place name, including “take me
to Rose Garden”, also works. Duplicate names require an ordinal or number.

“Take me there” works only when exactly one result is displayed. “Yes” alone does
not choose a place. Out-of-range, negative, mixed or ambiguous choices are rejected.
Number homophones such as “option too” are accepted with an explicit selection
phrase; bare “too” or “for” never selects a place.

To hear the list: “what did you find”, “read the options” or “say that again”.
To start another search: “new destination”, “find another place” or “go back”.

## Existing home and description tools

Home controls also accept “begin mapping”, “record my house”, “save the home map”,
“read my rooms”, “what rooms have I saved”, “call this room kitchen”, and ordinal
room selections. These phrases use the existing recording/recognition features;
they do not enable indoor walking guidance in the saved-home trial.

Camera-description commands include “describe my surroundings”, “tell me what you
see”, “recognise this room”, “give me a detailed description”, “explain tracking”,
and “stop the analysis”. Description and room recognition keep their existing
availability and verification rules. No new provider or paid API is introduced.

Commands use whole-phrase matching rather than guessing from a word anywhere in
a sentence. Negations are retained. Combined instructions such as “end walk or
show map” do not become an end-walk action.

## Validation

- Installed version 0.20.1, Android version code 41, on the connected phone.
- All 180 unit tests passed; Android lint reported no errors.
- Both live phone checks passed. They exercised a polite nearby-chemist request,
  a natural destination request, first-one selection, map/camera transitions,
  route overview, object scan, location and camera status, help, repeat and end
  navigation using the new phrases in the actual activity's command editor.
- Temporary test package removed; original sleep setting restored; the existing
  GuideLight button and Wispr accessibility services rebound after instrumentation.
- APK SHA-256: `3D2FAD097E5B4461030DCA7896825480A65134D3A4D167C1C1C3B77F520E1391`.
