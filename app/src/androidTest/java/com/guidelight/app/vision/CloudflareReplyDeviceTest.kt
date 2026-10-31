package com.guidelight.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Real account stays inside the phone. Only a generated test image is sent. */
@RunWith(AndroidJUnit4::class)
class CloudflareReplyDeviceTest {
    @Test fun actualProviderReturnsUsableSceneAndCalibrationAnswer()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val credentials=CloudflareConfigStore(context).read() ?: error("Connect Cloudflare first")
        val bitmap=Bitmap.createBitmap(192,256,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.RED)}
        val jpeg=ByteArrayOutputStream().use {out ->bitmap.compress(Bitmap.CompressFormat.JPEG,88,out);bitmap.recycle();out.toByteArray()}
        val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .addInterceptor {chain ->
                val response=chain.proceed(chain.request())
                val result=runCatching {Json.parseToJsonElement(response.peekBody(16_384).string()).jsonObject["result"]}.getOrNull()
                Log.i("GuideCloudTest","Synthetic provider result=${result.toString().take(1800)}")
                response
            }.callTimeout(20,java.util.concurrent.TimeUnit.SECONDS).build()
        val engine=CloudflareVisionEngine(credentials,client)
        try {
            val result=engine.analyze("Describe the visible scene and objects. No references are supplied; reference_match must be null.",jpeg,
                system="You are a JSON-only visual classifier. Return one valid JSON object without explanation or markdown.\n"+LocalAiPrompts.sceneSystem)
            val scene=LocalAiPolicy.parseScene(result.content)
            // This checks the transport contract, not room accuracy. Production
            // camera quality checks reject featureless frames before inference.
            assertTrue(scene.scene_type in LocalAiPolicy.scenes)
            assertNull(scene.reference_match)
            Log.i("GuideCloudTest","Scene contract passed in ${result.elapsedMs} ms")
            val calibration=engine.analyze("Name the dominant background color in this image. Reply with only RED, BLUE, GREEN or UNKNOWN.",jpeg)
            assertEquals("red",calibration.content.trim().trim('"','.',' ').lowercase())
            Log.i("GuideCloudTest","Calibration reply passed in ${calibration.elapsedMs} ms")
            val formatted=engine.analyzeScene("Describe this caption",jpeg,
                system="Describe the image in one short sentence, without JSON.",references=emptyList())
            LocalAiPolicy.parseScene(formatted.content)
            Log.i("GuideCloudTest","Prose formatting path passed in ${formatted.elapsedMs} ms")
        }finally{engine.close()}
    }
}
