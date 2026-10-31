package com.guidelight.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Actual installed model, not a stub. No room images or map modifications. */
@RunWith(AndroidJUnit4::class)
class LocalAiDeviceTest {
    @Test fun actualLocalModelThinksForTextAndSeesACalibrationImage() = runBlocking<Unit> {
        val engine=LocalVisionEngine(ApplicationProvider.getApplicationContext<Context>())
        try {
            assertTrue("Pinned model files must be installed",engine.installed)
            val text=engine.reason(LocalAiPrompts.statusSystem,
                "Verified facts: status=not_localized. A home map is saved. Camera tracking is ready. Current room is unknown. No walking route exists.",emptyList(),{})
            assertTrue(text.usedReasoning)
            assertTrue(LocalAiPolicy.statusAnswer(text.content,"not_localized").contains("not verified"))
            android.util.Log.i("GuideLocalAiTest","Actual text reasoning completed in ${text.elapsedMs} ms")
            val image=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(image);canvas.drawColor(Color.RED)
            canvas.drawText("TEST",60f,140f,Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.WHITE;textSize=40f})
            val jpeg=ByteArrayOutputStream().use { image.compress(Bitmap.CompressFormat.JPEG,90,it);it.toByteArray() }
            image.recycle()
            val vision=engine.reason("Inspect the supplied image. Think before answering, then return only JSON with dominant_color equal to red, blue, green, or unknown.",
                "What is the dominant background color?",listOf(jpeg),{})
            assertTrue(vision.usedReasoning)
            val start=vision.content.indexOf('{');val end=vision.content.lastIndexOf('}')
            assertTrue(start>=0 && end>=start)
            assertEquals("red",Json.parseToJsonElement(vision.content.substring(start,end+1)).jsonObject["dominant_color"]?.jsonPrimitive?.content)
            android.util.Log.i("GuideLocalAiTest","Actual image reasoning completed in ${vision.elapsedMs} ms")
        } finally { engine.close() }
    }
}
