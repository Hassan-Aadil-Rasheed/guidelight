package com.guidelight.app.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream

data class AiSnapshot(val nv21: ByteArray, val width: Int, val height: Int, val rotation: Int,
    val capturedAt: Long, val tracked: Boolean) {
    /** Called off the camera/render thread. */
    fun jpeg(maxSide: Int=256): ByteArray {
        val compressed=ByteArrayOutputStream()
        check(YuvImage(nv21,ImageFormat.NV21,width,height,null).compressToJpeg(Rect(0,0,width,height),88,compressed))
        val raw=BitmapFactory.decodeByteArray(compressed.toByteArray(),0,compressed.size()) ?: error("Camera image could not be read.")
        val rotated=if(rotation==0) raw else Bitmap.createBitmap(raw,0,0,raw.width,raw.height,Matrix().apply {postRotate(rotation.toFloat())},true)
        val scale=minOf(1f,maxSide.toFloat()/maxOf(rotated.width,rotated.height))
        val small=Bitmap.createScaledBitmap(rotated,(rotated.width*scale).toInt().coerceAtLeast(1),(rotated.height*scale).toInt().coerceAtLeast(1),true)
        return ByteArrayOutputStream().use { output ->
            small.compress(Bitmap.CompressFormat.JPEG,88,output)
            if(small!==rotated)small.recycle();if(rotated!==raw)rotated.recycle();raw.recycle()
            output.toByteArray()
        }
    }
}

object AiImageQuality {
    fun usableLuma(bytes: ByteArray, pixels: Int): Boolean {
        if(pixels<=0 || bytes.size<pixels)return false
        var total=0.0;var squared=0.0;var count=0
        for(i in 0 until pixels step 16) { val value=bytes[i].toInt() and 255;total+=value;squared+=value*value;count++ }
        val mean=total/count;val variance=squared/count-mean*mean
        return mean in 25.0..240.0 && variance>=20.0
    }
}
