package com.guidelight.app.indoor

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.guidelight.app.vision.AiRoomReference
import java.io.ByteArrayOutputStream

data class AiReferenceSheet(val jpeg: ByteArray, val rooms: List<AiRoomReference>)
data class AiSavedRoomPhoto(val reference: AiRoomReference,val jpeg: ByteArray)

/** Full upright thumbnails without room-name labels, for direct visual comparisons. */
fun SavedHomeStore.referencePhotos(): List<AiSavedRoomPhoto> {
    val file=current() ?: return emptyList()
    val saved=rooms(file).filter {it.room.lowercase(java.util.Locale.US) !in setOf("map","home map")}.distinctBy {it.room}.take(8)
    return SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use {db ->
        saved.mapIndexedNotNull {index,room ->
            db.rawQuery("SELECT Data.image,Data.calibration,Node.pose FROM Data JOIN Node USING(id) WHERE Data.id=?",arrayOf(room.node.toString())).use {cursor ->
                if(!cursor.moveToFirst())return@mapIndexedNotNull null
                val bytes=cursor.getBlob(0) ?: return@mapIndexedNotNull null
                val rotation=SavedRoomOrientation.rotation(cursor.getBlob(2),cursor.getBlob(1)) ?: return@mapIndexedNotNull null
                val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>640)sample*=2
                val raw=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=sample}) ?: return@mapIndexedNotNull null
                val upright=if(rotation!=0)Bitmap.createBitmap(raw,0,0,raw.width,raw.height,Matrix().apply {postRotate(rotation.toFloat())},true) else raw
                val scale=minOf(1f,384f/maxOf(upright.width,upright.height))
                val small=Bitmap.createScaledBitmap(upright,(upright.width*scale).toInt().coerceAtLeast(1),(upright.height*scale).toInt().coerceAtLeast(1),true)
                val jpeg=ByteArrayOutputStream().use {out ->small.compress(Bitmap.CompressFormat.JPEG,88,out);out.toByteArray()}
                if(small!==upright)small.recycle();if(upright!==raw)upright.recycle();raw.recycle()
                AiSavedRoomPhoto(AiRoomReference("R${index+1}",room.room),jpeg)
            }
        }
    }
}

/** Small local contact sheet; original home images never leave the phone. */
fun SavedHomeStore.referenceSheet(): AiReferenceSheet? {
    val photos=referencePhotos()
    if(photos.isEmpty())return null
    val rows=(photos.size+1)/2
    val sheet=Bitmap.createBitmap(480,rows*192,Bitmap.Config.ARGB_8888)
    val canvas=Canvas(sheet);canvas.drawColor(Color.WHITE)
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.BLACK;textSize=18f}
    val references=mutableListOf<AiRoomReference>()
    photos.forEachIndexed { index,photo ->
        val upright=BitmapFactory.decodeByteArray(photo.jpeg,0,photo.jpeg.size) ?: return@forEachIndexed
        val x=(index%2)*240;val y=(index/2)*192
        val id=photo.reference.id
        canvas.drawText("$id: ${photo.reference.name.take(20)}",x+6f,y+23f,paint)
        val scale=minOf(228f/upright.width,158f/upright.height)
        val width=(upright.width*scale).toInt();val height=(upright.height*scale).toInt()
        canvas.drawBitmap(upright,null,android.graphics.Rect(x+(240-width)/2,y+29,x+(240+width)/2,y+29+height),paint)
        references += photo.reference
        upright.recycle()
    }
    if(references.isEmpty()){sheet.recycle();return null}
    val bytes=ByteArrayOutputStream().use {sheet.compress(Bitmap.CompressFormat.JPEG,88,it);it.toByteArray()}
    sheet.recycle()
    return AiReferenceSheet(bytes,references)
}
