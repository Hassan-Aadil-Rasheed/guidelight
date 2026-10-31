# Camera focus and alert pacing — 0.21.1

Automatic camera alerts use an internal 3×3 partition of the normalized camera
image. There is no visible grid, extra control or TalkBack node. The middle
column is active from the top of the image to the bottom. The object's bounding
box centre chooses its column and row; merely touching the middle with one edge
does not qualify a side object. Invalid and tiny boxes are ignored.

The previous corridor-overlap rule could classify a large side object as a
hazard. It also ignored upper-camera objects. The centre-column rule now applies
consistently to warnings, their colours and automatic object descriptions in
both CameraX and the walking ARCore view. The projected route remains a visual
navigation aid; it does not move the camera's alert focus during a turn.

Objects in all three centre rows can be described as high, middle or low in the
camera image. Small detections use neutral descriptions. Existing apparent-area
thresholds still distinguish possible caution/stop warnings for larger central
detections. Apparent image size does not measure real-world size or distance.

A warning needs at least three consistent samples over 500 ms. The same risk and
vertical zone waits 30 seconds before repeating. Size-label jitter does not
restart it. A changed zone normally waits at least 8 seconds between warnings;
a stable escalation can interrupt sooner. A brief missing frame does not rearm
the warning. A 750 ms absence clears the active warning and allows a later
reappearance, subject to the minimum interval.

Ordinary automatic descriptions are suppressed while a central caution/stop
object is present, so they do not alternate with its warning. Speech remains
muted during Wispr input. An already-confirmed pending warning stays fresh only
while the same object remains visible during dictation. It is discarded when
the object disappears or the app leaves the foreground.

“Scan surroundings” still describes detected objects throughout the image,
including left and right. “Repeat alert” repeats the last warning on request.
Selfie/Wispr commands are unchanged. Focus follows where the rear camera points;
this remains an experimental secondary aid and can miss objects.

## Skill review

The curated Codex catalog had no Android camera-alert skill. The community
[Android accessibility skill](https://github.com/new-silvermoon/awesome-android-agent-skills/blob/main/.github/skills/ui/android-accessibility/SKILL.md)
was reviewed and applied: internal/decorative elements add no accessibility
focus targets, and existing voice controls and action descriptions remain intact.
No additional skill executable, paid API or runtime service was installed.

Also found [Senior Computer Vision](https://github.com/borghei/Claude-Skills/blob/main/engineering/senior-computer-vision/SKILL.md),
which covers detection evaluation and mobile model optimization. It is useful
for a later model-quality effort; this change adjusts image-region selection and
alert timing using the existing on-device detector, so no training pipeline was run.

## Validation

- Build and Android lint passed; all 197 unit tests passed.
- All 10 connected-phone tests passed: eight rendering checks plus the real
  walking/voice-command flow and camera-by-voice flow. They include invisible
  grid rendering, neutral side-object outlines, automatic TTS completion during
  walking and existing Selfie/Wispr text-command handling.
- A real bottle centred by the user was detected during the walking check.
  A camera utterance started and completed while navigation continued.
- Top/middle/bottom selection, side intrusion, small neutral detections,
  unstable frames, 30-second repetition, missing-frame grace and escalation
  were checked with deterministic unit inputs. These checks do not claim
  measured obstacle depth or universal object-detection accuracy.
- The temporary test package was removed. GuideLight and Wispr services were
  bound again, with no crashed services; the original screen-sleep setting was
  restored. The installed APK's checksum matches the exported APK.

Installed package: version code 43, version 0.21.1. Export:
`GuideLight-0.21.1.apk`, SHA-256
`776A1F5972E0B6E4FED2CE6CB5770E1C9F569BEDDCCDDEDFD414089FBC180F9A`.
