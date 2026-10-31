# Cloudflare camera assistance

## 0.16.2 caption recovery

The 0.16.1 real-camera check found that Llama Vision can still return a normal
caption instead of the requested JSON. Version 0.16.2 handles that case with
Cloudflare's JSON-mode-capable `@cf/meta/llama-3.1-8b-instruct`: it receives the
caption text, not another camera image, and extracts supported labels using the
existing schema. Application checks remove labels absent from positive caption
sentences, remove inferred left/right positions, and decline reference IDs not
explicitly present in the caption. Prose and movement instructions are never
spoken directly. The first image call is bounded to 12 seconds and formatting
shares a 15-second total budget. Descriptions still refer to the captured image;
this does not enable indoor walking directions or establish model accuracy.

Validation: 123 unit tests and Android lint passed. The connected provider test
passed in 4.769 seconds overall: scene-JSON transport in 2.209 seconds, color
calibration in 0.529 seconds, and natural-caption recovery including the second
model request in 1.823 seconds. All used a generated image. APK SHA-256:
`f69b477911da511c4cdd140c8a56c624f506b3a25a67764ebb456f8df77ff738`.

The installed production APK was hash-verified after removing the test package.
A real Home camera request then completed in 7.381 seconds and produced the
validated spoken-text description “a person, a cupboard,” consistent with the
visible test view. The result was no longer discarded as unfinished. Camera
tracking remained active; the 630 × 840 portrait preview and saved Cloudflare
connection were retained. The user confirmed hearing the description aloud.

## 0.16.1 reply-format repair

The reported “AI did not finish its answer” failure was reproduced with a live
Cloudflare image request. The adapter received a normal prose caption, which
the scene JSON parser declined. A controlled generated-image comparison found
the old combined plain prompt returned non-JSON in 3.140 seconds; separating
system/user chat messages returned parseable JSON in 1.325 seconds. That first
test also copied an illustrative chair from the prompt into a blank image's
answer. The repaired prompt no longer includes example object detections.

The live check then exposed a second response shape: Cloudflare had already
decoded the model JSON into `result.response` as an object. The old adapter only
accepted a string there and reported an incomplete answer. The repaired reader
accepts both a text response and a decoded JSON object, then applies the same
scene-policy validation. A unit regression fixture preserves this exact response
shape and checks that unsafe object labels remain rejected.

The adapter now sends system/user messages with the image, separates the scene
contract from the user question, and preserves result timing even if validation
fails. The initial model-license acceptance request retains Cloudflare's
documented `prompt: agree` format. Camera framing, stored credentials and map
data are preserved. A strict live-provider regression test checks an empty
synthetic scene and the setup color response through the repaired production
adapter; its final validation status is recorded below when complete. The
synthetic scene check tests reply structure, not visual accuracy. A probe
misclassified a featureless image; production's camera-quality gate rejects
featureless frames, and VLM room accuracy remains unvalidated.

Final 0.16.1 validation: 121 unit tests and Android lint passed. The connected
provider regression passed in 3.006 seconds overall: a structured scene response
was decoded and policy-validated in 2.236 seconds, and the setup color answer
passed in 0.575 seconds. These are generated-image transport checks, not a room
accuracy benchmark. APK SHA-256:
`cc24638ccd0c6ec337372b0655433b9fa825d85348d8518b3208351344268ab3`.

GuideLight 0.16.0 adds an optional Cloudflare Workers AI connection for Home
descriptions, saved-room visual comparisons and status explanations. Open Home
and choose **Connect Cloudflare**, or say **Cloudflare setup** through Wispr.

## Connect your personal test account

1. In the Cloudflare dashboard, open **Workers AI → Use REST API**.
2. Choose **Create a Workers AI API Token** and copy the token. The template is
   preferred; a custom token needs Workers AI Read and Edit for the intended account.
3. Copy the 32-character **Account ID** from the same page.
4. Enter both values on GuideLight's Cloudflare setup screen. Read the linked Meta
   license and acceptable use policy, and check the acceptance box if you agree.
5. Tap **Connect and test**. This submits the provider's required `agree` request,
   then analyzes a generated red image. No home-camera image is used for setup.
6. After the image test succeeds, return to Home and say **Describe the view** or
   **Identify this room** with two short Selfie presses.

The setup screen reports the image test's measured network/processing duration.
Actual camera latency and recognition quality must be evaluated separately.
Use **Disconnect Cloudflare** to remove the saved credentials and return to local
mode. **Detailed analysis (slow)** always remains the local LFM3 option.

## Implementation and limits

- Provider: `@cf/meta/llama-3.2-11b-vision-instruct`, native Workers AI execute-model
  endpoint on `https://api.cloudflare.com`. Account IDs are restricted to 32 hex
  characters. The app does not follow redirects or automatically retry requests.
- Normal cloud descriptions upload a current portrait image, resized to at most
  512 pixels on its longest side. Comparisons upload one composite containing the
  current image and a labeled local sheet of saved room views. There is no
  continuous video upload. These image types and prompts leave the phone only
  after a user requests an analysis with Cloudflare configured.
- The API token and account ID are AES-GCM encrypted with Android Keystore in the
  app's no-backup directory. Token entry is masked; the setup window blocks
  screenshots. Tokens are not built into the APK or written to logs. This is a
  personal testing connection; a distributed app should protect a shared account
  token behind its own authenticated backend.
- Requests have a 20-second total timeout. Camera results older than 15 seconds
  are declined. Canceling or leaving Home cancels the cloud call. There is no
  silent fallback to a second remote provider.
- The app validates structured scene labels before speech. The model is not
  listed among Cloudflare's JSON-mode supported models, so the adapter requests
  JSON in the prompt and retains strict application-side checks. A model response
  may be declined if it does not meet those checks. Cloud results do not claim a
  visible reasoning trace, a verified position, obstacle distance or a walking route.
- Existing local detection and localization do not wait for Cloudflare. The Home
  saved-map screen remains a recognition trial, without indoor walking guidance.
- Workers Free currently includes 10,000 Neurons daily. Quotas, queueing and
  connectivity may interrupt service. No billing plan is upgraded by this app.

Live Cloudflare inference cannot be verified until a user configures a valid
account and completes the image test. Credentials must be entered in the app,
not pasted into a chat or committed to the workspace.

Validation: the 0.16.0 build passed 120 unit tests and Android lint, including
mock-server checks for the authenticated image request, provider-envelope parsing,
quota errors, cancellation and account-path validation. An isolated connected
OPPO test passed Android Keystore encryption, readback, removal and absence of
plaintext token/account values from its stored file (0.234 seconds). This test
used an explicit cache fixture and fake credentials; the user's configuration
was not modified. APK SHA-256:
`264935beb781ed16078d6c1d5d928c1cfea26438ddf0b397c23b6238c9278f17`.

Sources: [REST setup](https://developers.cloudflare.com/workers-ai/get-started/rest-api/),
[vision model and license prerequisite](https://developers.cloudflare.com/workers-ai/models/llama-3.2-11b-vision-instruct/),
[image input example](https://developers.cloudflare.com/workers-ai/guides/tutorials/llama-vision-tutorial/),
[JSON-mode support](https://developers.cloudflare.com/workers-ai/features/json-mode/),
[free allowance](https://developers.cloudflare.com/workers-ai/platform/pricing/).
