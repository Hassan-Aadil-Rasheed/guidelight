package com.guidelight.app.navigation

import com.guidelight.app.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One pooled client; cancelling a screen request also cancels its network call. */
internal object GuideHttp {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS).build()

    suspend fun get(url: String): String = execute(Request.Builder().url(url))

    suspend fun postForm(url: String, key: String, value: String): String =
        execute(Request.Builder().url(url).post(FormBody.Builder().add(key, value).build()), 12_000)

    private suspend fun execute(request: Request.Builder, timeoutMillis: Long = 25_000): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request
            .header("User-Agent", "GuideLight/${BuildConfig.VERSION_NAME} (accessible pedestrian navigation MVP)")
            .header("Accept-Language", "en").build())
        call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        check(it.isSuccessful) { "Navigation service returned ${it.code}" }
                        val body = checkNotNull(it.body).string()
                        if (!continuation.isCancelled) continuation.resume(body)
                    } catch (error: Exception) {
                        if (!continuation.isCancelled) continuation.resumeWithException(error)
                    }
                }
            }
        })
    }
}
