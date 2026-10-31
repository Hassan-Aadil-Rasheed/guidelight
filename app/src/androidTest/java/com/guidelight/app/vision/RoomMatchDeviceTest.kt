package com.guidelight.app.vision

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guidelight.app.indoor.SavedHomeStore
import com.guidelight.app.indoor.referencePhotos
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Read-only saved-map check. The existing configured provider receives one saved view. */
@RunWith(AndroidJUnit4::class)
class RoomMatchDeviceTest {
    @Test fun distinctSavedRoomsCompeteInOneComparison()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val photos=SavedHomeStore(File(context.filesDir,"saved-home")).referencePhotos()
        val targets=photos.filter {it.reference.name in listOf("kitchen","balcony")}
        assertEquals("The recorded kitchen and balcony must be available",2,targets.size)
        val engine=CloudflareVisionEngine(CloudflareConfigStore(context).read() ?: error("Cloudflare must be connected"))
        try {
            for(target in targets) {
                val batch=CloudflareImage.roomComparison(target.jpeg,photos.map {it.reference to it.jpeg})
                val result=engine.identifyRoom(listOf(batch))
                Log.i("GuideRoomTest","All-room comparison target=${target.reference.id} complete=${result.complete} match=${result.comparison.reference_match} ambiguous=${result.comparison.ambiguous} elapsedMs=${result.elapsedMs}")
                assertTrue("Comparison failed: ${result.errorMessage}",result.complete)
                assertEquals("Known view was not distinguished: ${result.comparison.evidence}",target.reference.id,result.comparison.reference_match)
                assertFalse(result.comparison.ambiguous)
            }
        }finally{engine.close()}
    }
    @Test fun knownSavedImageCanBeComparedWithoutRoomNamesInThePrompt()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val photos=SavedHomeStore(File(context.filesDir,"saved-home")).referencePhotos()
        assertTrue("Saved reference photos must be available",photos.isNotEmpty())
        val reference=photos.first()
        val batch=CloudflareImage.roomComparison(reference.jpeg,listOf(reference.reference to reference.jpeg))
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(batch.jpeg,0,batch.jpeg.size,bounds)
        assertEquals(480,bounds.outWidth);assertEquals(352,bounds.outHeight)
        val engine=CloudflareVisionEngine(CloudflareConfigStore(context).read() ?: error("Cloudflare must be connected"))
        try {
            val result=engine.identifyRoom(listOf(batch))
            Log.i("GuideRoomTest","Saved-image comparison complete=${result.complete} match=${result.comparison.reference_match} ambiguous=${result.comparison.ambiguous} elapsedMs=${result.elapsedMs} error=${result.errorMessage}")
            assertTrue("Comparison request must finish: ${result.errorMessage}",result.complete)
            assertEquals("An identical saved view must match its ID",reference.reference.id,result.comparison.reference_match)
        }finally{engine.close()}
    }
}
