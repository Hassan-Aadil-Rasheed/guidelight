# Saved-room identification

**Identify room** now has its own Cloudflare comparison path. It no longer uses
the generic object-description answer to decide which saved room resembles the
current view. The existing Describe view path and local LFM fallback are retained.

Saved room photographs are read from the existing home database without modifying
the map or its labels. Up to eight usable distinct room labels are considered.
In 0.17.1, the current portrait view and all usable saved room views are compared
in one grid, labeled CURRENT and R1/R2/etc. CURRENT is explicitly the top-left
panel. Room names are not included in the image or model question. Each panel
preserves its image's aspect ratio. A single comparison makes the saved rooms
compete together rather than collecting a separate winner from each pair.

Each image request has a maximum
15-second timeout and the complete search shares a 32-second budget. The visual
answer is capped at 128 tokens to avoid a long unneeded description. A caption
can be converted into the dedicated room schema using the existing Cloudflare
text formatter; it cannot create a saved-view match unless the visual response
explicitly makes that comparison. All active requests are canceled when Home
is left or analysis is canceled. Only manual room-identification requests upload
these comparison images; there is no continuous upload loop. A search can make
multiple calls against the account's free allowance.

The saved-image audit found that all six retained references had sideways
content even though their JPEG dimensions were portrait. The loader now projects
the map's Z-up vector through the recorded node pose and camera calibration and
uses the nearest quarter-turn to display each stored image upright. Pixel aspect
ratio is no longer used to guess orientation. Invalid orientation metadata or a
view directed almost vertically is skipped. The same corrected references serve
the local fallback. The original database and labels are read-only.
The calibration layout was checked against RTAB-Map's
[CameraModel serialization](https://github.com/introlab/rtabmap/blob/master/corelib/src/CameraModel.cpp).

The user question asks for the actual result fields without supplying a sample
answer to copy. This helps avoid a long explanation using the response budget
before the match is stated. Incomplete searches show a failure message rather
than a successful completion status. Contradictory or uncertain evidence clears
a claimed match.

The app accepts only supplied reference IDs with stated fixed visual details.
Room categories and common furniture alone do not establish a match. Conflicting
labels, ambiguous comparisons and incomplete searches do not become location
claims. Matching one room is announced as visual resemblance unless a fresh
native saved-map fix independently confirms the same label. Unknown results
give a room-specific explanation rather than falling back to object descriptions.

This remains a recognition trial, not indoor turn-by-turn navigation. A correct
transport reply or a saved-image self-match does not establish real-world room
accuracy across viewpoints, similar rooms or changed lighting.

The focused tests cover unknown IDs, category-only guesses, caption grounding,
contradictory evidence, conflicting matches, incomplete searches and concurrent
request completion. The connected-phone check compares an existing saved image
against itself.

A direct provider experiment bypassing the camera's quality guard with a blank
CURRENT panel returned a false match. It is recorded in
`.tools/lfm/0170-room-negative-experiment.txt`. This is a model limitation, not a
passing accuracy result. The production ARCore snapshot path checks
`AiImageQuality.usableLuma` before returning an image and rejects dark, overexposed
and featureless input; existing unit tests exercise those cases. A structured
model reply with stated features still is not independent proof that those
features match. Real-room recognition across different viewpoints remains a
user trial, and AI resemblance does not enable walking guidance or establish a
tracked position.

Version 0.17.1 follow-up:

- Read-only inspection of all six saved reference pictures confirmed their
  sideways orientation. The recorded pose and calibration each resolved to a
  90-degree correction; a visual audit confirmed the corrected pictures upright.
- The user confirmed that `room` and `second room` are different places, and that
  the current location for the next live check is named `room`. Labels are kept
  separate and the map database is unchanged.
- Five orientation unit tests cover quarter-turns, aspect ratio independence,
  the actual recorded orientation pattern, invalid metadata and vertical views.
  All 133 unit tests and Android lint passed. Both APKs assembled successfully.
- Both instrumentation tests passed on the OPPO after installation: the
  single-reference self-match completed in 2,057 ms; comparisons against all six
  references correctly selected kitchen (R3, 3,375 ms) and balcony (R6, 3,966 ms).
  These known-image checks demonstrate candidate discrimination, not accuracy on
  unseen camera angles.
- Normal APK: `GuideLight-0.17.1.apk`, SHA-256
  `892d80503d907e857e4357d627e443d8a76412531e76d77cb6f6c25083de6c2a`.
- The normal 0.17.1 APK was restored after removing the test package. Both Wispr
  Flow and GuideLight button control were bound with no crashed services. The
  saved-map checksum remained unchanged.
- The live `room` check was rejected by the existing camera-quality check as too
  dark before any provider request was made. The user cannot add light now, so
  actual-room recognition at a new camera angle remains unverified. The camera
  check was ended; no remapping or label changes are required for this update.

Provider format reference: [Cloudflare JSON Mode](https://developers.cloudflare.com/workers-ai/features/json-mode/).
The vision model is not on its supported-model list; the caption formatter uses
the supported Llama 3.1 text model. Neither request can bypass the app's match
validation.

Validation on the connected OPPO CPH2505 (29 October 2026):

- 128 unit tests passed; debug APK, instrumentation APK and Android lint passed.
- The production room request matched an identical saved view (R1) in 1,883 ms.
  The instrumentation test passed. This is a pipeline check, not an accuracy score.
- A live Home camera comparison finished in 4,557 ms and returned the ambiguous
  room message. No particular room or tracked position was claimed. Confirmation
  of the actual room and audible result is pending the user's trial.
- The normal APK was restored after removing the test package. Wispr Flow and
  GuideLight button control were bound with no crashed accessibility services.
- The saved-map SHA-256 remained
  `6a7618562cc7dab0da88c80335b0e3debb75aa4944768c54c5a360188932a3bf`.
- Version 0.17.0 (34), installed APK SHA-256:
  `8df23cf4f2e73e003a06b02ef58aeb5c689c623cb3bfd87472825b735ce2f25a`.

Speech follow-up: the user initially did not hear the room result. At inspection,
the Wispr bridge was IDLE, the speech engine was initialized, the media stream
was unmuted and routed to the speaker. `SpeechPlaybackDeviceTest` passed on the
phone, with offline English synthesis and nonzero PCM output (peak 16,677), and
the user confirmed hearing both direct TTS and file playback. The test package
was removed and the normal APK restored. A later room retry was interrupted when
Home left the foreground; the expected `onPause` behavior canceled analysis and
stopped speech. A controlled foreground room-result replay is pending. No speech
source change has been made based on an unconfirmed audio failure.

The controlled foreground check then completed in 4,613 ms and returned the
ambiguous room message. Android recorded its speech playback starting and
finishing on the built-in speaker, with Home still foreground and the Wispr
bridge IDLE. The user explicitly confirmed hearing this actual room result.
Speech playback is therefore confirmed for this check; accurate identification
of the user's current saved room remains unverified.
