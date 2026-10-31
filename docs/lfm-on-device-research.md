# Liquid models for GuideLight — research, 28 October 2026

Recommendation: evaluate LFM3-VL-2B locally as a scene-understanding and room-reference
comparison assistant. Benchmark it against LFM2.5-VL-450M on the OPPO before choosing
the default. A separate text model is optional; the vision model already accepts
text. This is a feasibility study, not a completed integration or accuracy result.

## Verified models and files

Liquid announced LFM3 on 27 October 2026. Its official repositories include the
following local-inference candidates. Decimal sizes below are file downloads,
not total runtime RAM. Exact revisions, bytes and published SHA-256 hashes are
in [lfm-model-manifest.json](lfm-model-manifest.json). No weights were downloaded.

| Candidate | Files to evaluate | Combined download |
| --- | --- | ---: |
| LFM3-VL-2B | QAD-Q4_0 backbone + matching Q8_0 projector | 1.253 GB |
| LFM3-VL-2B | QAD-Q4_0 backbone + matching F16 projector | 1.429 GB |
| LFM3-1.7B, optional text-only | QAD-Q4_0 | 1.050 GB |
| LFM2.5-VL-450M, smaller baseline | Q4_0 backbone + matching Q8_0 projector | 322 MB |
| LFM2.5-VL-450M, smaller baseline | Q4_0 backbone + matching F16 projector | 408 MB |

Use the corresponding repository's projector, not one from a different model.
The QAD checkpoint is Liquid's quantization-aware distilled version. Evaluate
projector quantization as an accuracy/latency choice rather than assuming they
are equivalent. Sources: [LFM3 vision files](https://huggingface.co/LiquidAI/LFM3-VL-2B-GGUF/tree/main),
[LFM3 text files](https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/tree/main),
[450M files](https://huggingface.co/LiquidAI/LFM2.5-VL-450M-GGUF/tree/main).

LFM3-VL supports text, images and video; it is a generative model with reasoning.
Reasoning may add substantial latency. Its video sampling rate is not a measured
phone inference rate. Liquid's published edge tests use newer hardware, including
a Galaxy S26 Ultra, and do not establish performance on our Snapdragon 695.
Source: [official vision model card](https://huggingface.co/LiquidAI/LFM3-VL-2B).

## Actual phone constraints

Read-only ADB checks during this research found CPH2505 / SM6375, approximately
7.2 GiB physical RAM exposed to Android, 1.66 GiB currently available memory,
3.8 GB reported available data storage, and 4 KiB pages. Available memory is a
momentary system estimate, not a guaranteed allocation limit. OPPO identifies
this model as Snapdragon 695 with Adreno 619 and 8 GB nominal physical RAM.
[Official OPPO specifications](https://www.oppo.com/id/smartphones/series-reno/reno8-t-5g/specs/).

My inference: a quantized LFM3 trial is plausible, but concurrent mapping, camera,
Wispr and model execution may cause memory pressure or thermal throttling. Start
with one loaded model, short context and still-image requests. Measure before
promising responsiveness. The smaller vision model provides a useful baseline
under the current free-memory/storage conditions. Extra virtual RAM is not a
replacement for physical RAM bandwidth.

## Android runtime

Use upstream llama.cpp, pinned to b11960 / v0.8.0 or a verified compatible later
commit, with its mtmd multimodal library. Liquid's GGUF card explicitly names
this minimum release. I checked GitHub's API: implementation PR #31252 merged
on 27 October, and b11960 contains that merge. The release includes generic
Android arm64 CLI/benchmark binaries. Some older base-card instructions still
say support is in review; the updated GGUF card and merged source are newer.
[Model runtime requirements](https://huggingface.co/LiquidAI/LFM3-VL-2B-GGUF),
[implementation PR](https://github.com/ggml-org/llama.cpp/pull/31252),
[release](https://github.com/ggml-org/llama.cpp/releases/tag/b11960).

For the app, use NDK/CMake and a small Kotlin/JNI adapter around upstream llama
and mtmd. Enable LLAMA_BUILD_MTMD; the text-only AiChat sample alone is not the
image integration. Start with the generic arm64 CPU backend; test Vulkan/OpenCL
separately. Do not assume the Snapdragon-specific prebuilt accelerator supports
the 695. Download models to real app-private files through WorkManager or
DownloadManager, with resume, pinned revisions and hash validation.
[Liquid Android guide](https://docs.liquid.ai/deployment/on-device/llama-cpp/mobile),
[multimodal C API](https://docs.liquid.ai/deployment/on-device/llama-cpp/multimodal).

Our app currently has no NDK build configuration and already bundles RTAB-Map's
native libraries, including OpenMP. Integration must check native-library naming
and OpenMP compatibility, and benchmark thread contention with the mapper. Reuse
the existing ARCore camera stream: copy a requested, reduced-resolution snapshot
and release camera images promptly. Do not open a second camera or run inference
on the render thread. Keep at most the latest pending request.

## Proposed recognition design

1. Keep a few user-labeled reference views for each saved room and doorway.
   Obtain independent views for evaluation; training/reference images cannot
   serve as the only test images.
2. On “What do you see?” analyze one recent image for room type, visible objects,
   and a brief description. Use explicit unknown output for poor/covered images.
3. On “Where am I?” compare a small candidate set of saved room references with
   the current view, alongside existing visual localization. Avoid placing the
   whole 157-frame map in the language context.
4. Validate structured output against saved room IDs and reference IDs. Generated
   confidence percentages are not calibrated localization evidence. Require
   measured matching evidence and temporal consistency for a saved-room claim.
5. Preserve an unknown state if the scene suggests a room category but does not
   identify a particular saved room. A generic kitchen description cannot establish
   that the person is in their saved kitchen or which doorway to use.
6. Use tracking, map geometry and a fast detector/depth pipeline for movement,
   distances and immediate obstacle alerts. VLM descriptions can supplement
   object names; they do not provide validated centimetres/millimetres or a safe
   traversable route from an ordinary image.

These are proposed integration decisions, not capabilities already verified in
GuideLight. Preserve Wispr as the requested speech-input path and Android TTS as
speech output. Local LFM inference does not make Wispr or outdoor map services
offline. Immediate commands such as End walk should retain deterministic handling;
an optional text model may normalize more varied language into validated intents.

## Small benchmark before integration

Run one model at a time on the phone, initially with 384/512-pixel images, small
context, short outputs and CPU thread counts matched to the device. Benchmark
both reasoning settings where supported; truncating a reasoning trace without
a usable answer counts as a failed request. Test the real multimodal runner,
since text-only tokens/second omits image processing.

Measure image encoding, time to first token, time to a usable final answer,
peak memory, sustained temperature/throttling, battery impact and map-frame rate.
Repeat with camera/mapping/Wispr active. Evaluate held-out room images, similar
rooms, doorways, changed lighting, furniture movement and unknown rooms. Count
wrong-room announcements separately from correctly declining to identify a room.
Compare against current localization without the model. Only enable a feature
when it improves measured results under an agreed latency budget.

The research above preceded implementation. GuideLight 0.14.0 now integrates
on-demand local text and vision inference, with a connected-phone calibration
test completed. See [implementation and validation](local-ai.md) for the tested
scope and remaining room-recognition limitations.

## Cost and license

Local inference requires no paid hosted inference API. The models are open-weight
under LFM Open License v1.0, not an unrestricted Apache-2.0 model license. Liquid's
guide permits research use and describes free commercial use below $10 million
annual revenue, with a separate license needed at/above that threshold. Preserve
the model license and attribution when distributing.
[Official license guide](https://docs.liquid.ai/lfm/help/model-license).
