# GuideLight MVP plan

## Product goal

Validate whether a phone mounted at chest height can provide a blind user with useful voice-driven destination setup and supplementary awareness cues, without claiming to replace a primary mobility aid.

## MVP boundary

### Included

1. Permission and readiness flow for the camera; Wispr Flow owns its own microphone permission.
2. Voice destination capture through a normal text field compatible with Wispr Flow's Android bubble.
3. Destination extraction from a short command.
4. In-app nearby-place disambiguation and pedestrian route instructions.
5. Foreground camera preview with generic object detection.
6. Rate-limited spoken and haptic obstacle warnings.
7. TalkBack labels, large touch targets, high contrast, and status announcements.

### Deliberately excluded

- Autonomous or safety-certified navigation.
- Crossing traffic-light interpretation.
- Curb, stair, pothole, branch, and vehicle-specific recognition.
- Production-grade rerouting, offline maps, and poor-GPS recovery.
- Hindi input/output.
- Raspberry Pi or camera-glasses hardware.
- Always-on/background camera and microphone use.

## Why Wispr MCP cannot provide destination transcription

The available Wispr Flow settings expose an MCP connector at `https://api.wisprflow.ai/connect/mcp`. The same screen defines its scope: AI tools may access Notetaker meeting notes and transcripts, while the MCP has no access to dictations.

MCP is a tool protocol, not an automatic replacement for a streaming speech-to-text service. This connector cannot accept the app's live microphone stream and cannot return a newly dictated destination. Adding it to the Android app would therefore add authentication and network complexity without implementing the required feature.

The zero-key prototype instead uses Wispr Flow's existing Android behavior: GuideLight focuses its destination field, then Flow's bubble records speech and inserts the resulting text into that field. After the inserted text remains stable briefly, GuideLight parses it and starts navigation automatically. This requires no Wispr API or MCP integration.

Later fully integrated transcription candidates are:

- an on-device Whisper implementation for predictable offline behavior;
- a hosted transcription API, if user consent, connectivity, cost, and key security are addressed;

API keys must never be embedded in the APK. A hosted provider requires a small authenticated backend or short-lived tokens.

The Wispr MCP may be relevant only for a separate, non-navigation feature such as “search my past meeting notes.” It should not be used for destination capture, obstacle warnings, or any safety-critical operation.

## Delivery phases

### Phase 1 — current prototype

- Wispr-compatible voice destination entry, nearby-place selection, in-app blind-profile pedestrian instructions, directional obstacle warnings, TTS, and haptics.
- Camera route-corridor overlay that visually bends with the active turn instruction and gates obstacle alerts to that corridor.
- Unit tests for destination parsing and obstacle-risk rules.
- Bench testing only; no unsupervised mobility use.

### Phase 2 — hardened navigation

- Replace public demo services with self-hosted or contracted OpenStreetMap search and Valhalla routing.
- Add heading correction, GPS-accuracy gating, off-route recovery, and concise turn announcements.
- Cache the active route for temporary network loss.

### Phase 3 — meaningful perception

- Replace CameraX with an ARCore session and enable the Depth API on supported devices such as the OPPO Reno8 T.
- Measure median depth in left, centre, and right walking corridors; report distance and obstacle position without prescribing an unsafe movement direction.
- Collect consented, representative chest-camera test data.
- Replace generic detection with task-specific classes and/or depth estimation.
- Add warning prioritization so route speech and hazard speech do not overlap.
- Measure latency, false positives, false negatives, heat, and battery use across target phones.

### Phase 4 — user validation

- Run supervised sessions with blind participants and an orientation-and-mobility specialist.
- Record task completion, missed hazards, nuisance alerts, cognitive load, and trust calibration.
- Define an explicit go/no-go safety threshold before outdoor expansion.

## Acceptance criteria for Phase 1

- A first-time user can grant or deny each permission without becoming trapped.
- The primary action is reachable and understandable with TalkBack.
- At least 9 of 10 clearly spoken English test destinations are parsed correctly in a quiet room on the chosen test phone.
- A generic query presents nearby choices before navigation.
- Selecting a place starts in-app blind-profile walking instructions without hiding the camera.
- Camera analysis does not queue stale frames.
- Warnings require repeated evidence and occur no more than once every 4.5 seconds.
- The safety notice is visible and spoken on launch.
- The app remains responsive for a continuous 20-minute device test.

## Key risks and mitigations

| Risk | MVP mitigation |
|---|---|
| User over-trusts warnings | Persistent safety notice; conservative language; supervised testing only |
| False or missed obstacle | Three-frame confirmation; no distance claims; repeat-warning control |
| Network loss | Android TTS remains local when installed; show an actionable route error and plan active-route caching |
| Speech and warning collision | Hazard warnings flush lower-priority speech |
| API-key leakage | No API key in Phase 1; backend required for future hosted speech |
| Battery and heat | 640×480 analysis, latest-frame backpressure, foreground-only lifecycle |

## Recommended next engineering decision

Harden the routing stack before unsupervised trials: replace demo endpoints, add rerouting and GPS-accuracy checks, cache active routes, and test instructions with an orientation-and-mobility specialist.

