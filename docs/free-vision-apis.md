# Free vision API options checked 29 October 2026

These are hosted image-understanding services, not on-phone inference. Using one
would upload selected camera frames and prompts. GuideLight 0.16.0 now includes
an optional [Cloudflare connection](cloudflare-setup.md). A valid user account
must complete its image test before live latency or accuracy can be claimed.

| Service / model | Free allowance verified in official documentation | Fit for GuideLight |
| --- | --- | --- |
| Gemini `gemini-3.5-flash-lite` | Standard text/image/video/audio input and output have a free tier; active project quotas are shown in AI Studio. | First candidate for occasional scene descriptions and visual comparisons. |
| Groq `qwen/qwen3.8-27b` | Listed free limits: 30 requests/minute, 1,000/day, 8,000 tokens/minute and 200,000/day. Each image counts as 2,048 input tokens. | Supports vision plus thinking/instruct modes, but image token limits bind much earlier than request counts. |
| Cloudflare `@cf/meta/llama-3.2-11b-vision-instruct` | Workers Free includes 10,000 Neurons/day shared across models; free-plan requests stop when the allowance is exhausted. | Alternative vision API using an API token and account ID. Initial model-license acceptance is required. |

Gemini's free-tier content is marked as used to improve Google's products. This
matters for private home images. Provider privacy and account quota settings
must be considered before uploading any camera frame. Free allowances are not
an unlimited continuous video service or a latency guarantee.

Keep immediate local detector alerts and verified localization independent of
any cloud response. Remote descriptions may arrive late, hit quotas or lose
connectivity; a late response must not become a current walking instruction.
Send a current frame on request, use bounded outputs/timeouts, and discard stale
results. Measure end-to-end capture/upload/answer time with a user-provided key
before choosing a provider. A production shared key belongs behind an authenticated
backend, not embedded in an APK.

Sources:

- [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing)
- [Gemini project limits](https://ai.google.dev/gemini-api/docs/rate-limits)
- [Groq vision and image token accounting](https://console.groq.com/docs/vision)
- [Groq limits](https://console.groq.com/docs/rate-limits)
- [Cloudflare pricing](https://developers.cloudflare.com/workers-ai/platform/pricing/)
- [Cloudflare Llama Vision](https://developers.cloudflare.com/workers-ai/models/llama-3.2-11b-vision-instruct/)
