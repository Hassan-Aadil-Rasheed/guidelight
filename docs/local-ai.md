# Local AI in GuideLight

## 0.15.0 faster default (build checked; full Home trial pending)

Normal **Describe view** and **Identify room** requests now select
LFM2.5-VL-450M Q4_0 with its Q8_0 projector, pinned to revision
`1abed04b6fe71314d8c446a1371c03d7c332266d`. This fast model does not produce a
reasoning trace; the app reports its reasoning flag truthfully. **Detailed
analysis (slow)** / **Describe the view in detail** retains LFM3 reasoning.
Only one model process is kept active when switching modes. The scene decoder
uses a JSON schema constrained to the app's object labels, directions and supplied
reference IDs; Kotlin still validates the answer before speech. This formatting
constraint does not prove that a visual identification is correct.

Both new weights were SHA-256 checked on the phone. A standalone packaged-runner
calibration on the OPPO loaded the fast model in 3.2 seconds, identified a synthetic
red image in 4.91 seconds, and repeated that identical cached request in 0.67
seconds. The cache result does not measure new-frame latency. This test did not
run simultaneous Home mapping or prove real-room accuracy. The build passed 115
unit tests and Android lint. The portrait 3:4 preview is retained.

Hosted alternatives are researched separately in [free vision APIs](free-vision-apis.md).

## Earlier LFM3 trial and framing verification

Home includes on-demand camera descriptions, comparison with saved room views,
and explanations of the current tracking status. Use two short Selfie presses
with Wispr to say **Describe the view**, **Identify this room**, or **Explain my
status**. **Cancel analysis** stops a pending request. The same actions are
available in the Home screen's Local AI card.

Keep the phone still while requesting an image description. This is a captured
view, not a continuous obstacle-monitoring feed. If the image is too dark or
featureless, capture is unavailable, or the model cannot return a usable answer,
GuideLight reports the problem rather than announcing a room or direction.

## Model and runtime

- LiquidAI LFM3-VL-2B, QAD Q4_0 language weights and Q8_0 vision projector,
  pinned to Hugging Face revision `017b19da2dcffadb41d67745941f3d785f0d162f`.
- Official llama.cpp Android arm64 runtime b11960, packaged with the app.
- Both text and image requests use the same vision-language model. A second
  text-only model is unnecessary for this trial.
- Reasoning is enabled with a 96-token reasoning budget and a bounded final
  answer. GuideLight requires reasoning content and a usable final response;
  it does not display or speak the internal reasoning trace.
- Inference uses an app-owned process, loopback-only connection and per-process
  key. Camera images, saved room references and prompts are processed on the
  phone. Model downloads require internet when weights are missing. Wispr and
  outdoor search retain their existing internet requirements.
- Model files are SHA-256 checked and excluded from Android backup. Licenses
  and runtime provenance are packaged in `assets/local-ai`.

## Evidence and limits

The model classifies a fresh ARCore camera frame into validated scene/object
labels. For room identification it can also compare a local contact sheet of up
to eight saved room views. Visual resemblance is announced as resemblance; it
does not establish a verified position. Verified location still requires fresh
native saved-map recognition. Conflicting visual and tracking evidence does not
silently override that distinction.

The model cannot issue arbitrary walking, crossing, distance or safe-path
instructions through this feature. Speech is assembled from validated labels
and actual tracking-state templates. Indoor walking guidance remains unavailable
in the saved-map recognition trial.

The final build passed 114 unit tests and Android lint. A connected OPPO CPH2505
test completed real text and synthetic-image inference with reasoning present,
including identifying a red calibration image. The combined test took 69.091
seconds, including setup, model loading and verification; this is not a measured
per-request latency or a continuous-camera frame rate. Shutdown completed cleanly.
Real-room accuracy, changed lighting, similar rooms, thermal behavior and
simultaneous camera/mapping performance still require physical evaluation.

The first full Home camera trial in 0.14.0 reached the 180-second request limit.
Runner timing showed 92.68 seconds of prompt processing for 689 tokens before
generation. Version 0.14.1 reduces image input to 256 pixels, requests a 64–96
image-token budget, shortens the scene prompt and limits output to three objects.
It temporarily pauses new native map-recognition frames during stationary image
analysis, while retaining the ARCore preview and snapshot stream. Recognition
evidence is cleared and must be reacquired afterward; map files are preserved.
AI requests are unavailable during map recording, so recording is not silently
interrupted.

The updated 0.14.1 Home camera trial completed a real image request in 35.320
seconds with reasoning present and a usable structured answer. Runner timing
reported 293 prompt tokens, 9.470 seconds of prompt evaluation and 25.747 seconds
of generation. This confirms the capture-to-answer path and improved latency for
one request; it does not establish the correctness of its object labels or a
general latency guarantee.

A second description completed in 34.728 seconds with reasoning and a usable
answer. A two-image saved-reference request completed model generation in
35.322 seconds, but a subsequent user request replaced the displayed result
before verification; successful room identification is not claimed.

## Camera field of view on the test phone

GuideLight uses rear camera ID 0 on the OPPO CPH2505. Its AI snapshot includes
the entire CPU camera image, rotated upright and resized without a region crop.
Through 0.14.1, the short camera preview was a display viewport and was not the AI's input crop.
Downscaling preserves coverage while reducing detail.

Version 0.14.2 changes Home to a centered portrait 3:4 preview (the upright form
of a 4:3 camera frame). It renders the full CPU YUV image also used for AI
snapshots, instead of cropping the separate 16:9 GPU camera texture. Preview
refresh is capped at approximately 12.5 fps while the AR session keeps its normal
tracking stream. The preview shrinks proportionally during voice entry. The
model input continues to preserve the full CPU image aspect ratio.

Installed and visually checked on the OPPO on 29 October 2026: the live
GLSurfaceView measured 630 × 840 pixels (3:4), fed by a 640 × 480 CPU stream.
The preview showed an upright, undistorted scene and camera tracking continued.
Version 0.14.2 passed 114 unit tests and Android lint. Its APK SHA-256 is
`a0ed3c34ca60dd4af3ee55ca3cab29fcc218461b6a7fa798ca48b392a4d8cb86`.

The CPH2505 has no separate ultrawide rear camera. OPPO lists an 84-degree main
camera, a depth camera and a microlens. Device camera metadata confirms the
active main camera's 5.24 mm focal length and 7.68 × 5.76 mm sensor; the other
rear entries are auxiliary cameras, not an ultrawide replacement. Software
cannot switch this phone to a nonexistent 0.5× optical camera.
[OPPO CPH2505 specifications](https://www.oppo.com/in/smartphones/series-reno/reno8-t-5g/specs/).

Version 0.14.1 passed the same 114 unit tests and Android lint, and was installed
on the OPPO on 29 October 2026. Its APK SHA-256 is
`831fc074e42f60e66040eaf6ad026028176ac8a63ee0b0c222c766a16f3ae91c`.

See [research and sources](lfm-on-device-research.md) and
[model/runtime manifest](lfm-model-manifest.json).
