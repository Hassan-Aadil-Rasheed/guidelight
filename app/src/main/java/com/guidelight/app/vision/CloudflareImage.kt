package com.guidelight.app.vision

import android.graphics.*
import java.io.ByteArrayOutputStream

/** One image for the provider: current view above clearly labeled saved references. */
object CloudflareImage {
    fun roomComparison(current: ByteArray,references: List<Pair<AiRoomReference,ByteArray>>): RoomComparisonImage {
        require(references.size in 1..8)
        val images=listOf("CURRENT" to current)+references.map {it.first.id to it.second}
        val columns=minOf(images.size,3)
        val panel=if(images.size<=3)240 else 320
        val rows=(images.size+columns-1)/columns
        val output=Bitmap.createBitmap(columns*panel,rows*352,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(output);canvas.drawColor(Color.WHITE)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.BLACK;textSize=24f}
        images.forEachIndexed {index,(label,bytes) ->
            val image=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: error("A saved room image could not be read.")
            val x=(index%columns)*panel;val y=(index/columns)*352
            canvas.drawText(label,x+8f,y+26f,paint)
            val scale=minOf((panel-8f)/image.width,312f/image.height)
            val w=(image.width*scale).toInt();val h=(image.height*scale).toInt()
            canvas.drawBitmap(image,null,Rect(x+(panel-w)/2,y+36,x+(panel+w)/2,y+36+h),paint);image.recycle()
        }
        val jpeg=ByteArrayOutputStream().use {out ->output.compress(Bitmap.CompressFormat.JPEG,88,out);out.toByteArray()}
        output.recycle();return RoomComparisonImage(jpeg,references.map {it.first})
    }
    fun withReferences(current: ByteArray,references: ByteArray): ByteArray {
        val view=BitmapFactory.decodeByteArray(current,0,current.size) ?: error("The camera image could not be read.")
        val sheet=BitmapFactory.decodeByteArray(references,0,references.size) ?: error("Saved room images could not be read.")
        val width=480;val viewHeight=384;val header=32
        val output=Bitmap.createBitmap(width,viewHeight+sheet.height+header*2,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(output);canvas.drawColor(Color.WHITE)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.BLACK;textSize=22f}
        canvas.drawText("CURRENT VIEW",8f,25f,paint)
        val scale=minOf(width.toFloat()/view.width,viewHeight.toFloat()/view.height)
        val w=(view.width*scale).toInt();val h=(view.height*scale).toInt()
        canvas.drawBitmap(view,null,Rect((width-w)/2,header,(width+w)/2,header+h),paint)
        canvas.drawText("SAVED REFERENCES",8f,(header+viewHeight+25).toFloat(),paint)
        canvas.drawBitmap(sheet,0f,(viewHeight+header*2).toFloat(),paint)
        return ByteArrayOutputStream().use {out ->
            output.compress(Bitmap.CompressFormat.JPEG,88,out);view.recycle();sheet.recycle();output.recycle();out.toByteArray()
        }
    }
}
