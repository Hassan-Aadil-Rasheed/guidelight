package com.guidelight.app.indoor

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Read RTAB-Map's mono calibration and project world-up into the stored image.
 * CameraModel serialization: int[11], calibration doubles, local float[12].
 * The map uses ROS Z-up; image dimensions alone do not describe its orientation.
 */
object SavedRoomOrientation {
    fun rotation(pose: ByteArray?, calibration: ByteArray?): Int? {
        if(pose?.size!=48 || calibration==null || calibration.size<44)return null
        val bytes=ByteBuffer.wrap(calibration).order(ByteOrder.LITTLE_ENDIAN)
        val header=IntArray(11){bytes.int}
        if(header[3]!=0 || header[4]<=0 || header[5]<=0 || header[10]!=12)return null
        if(header[6]!=9 || header[7] !in 0..14 || header[8] !in listOf(0,9) || header[9] !in listOf(0,12))return null
        val offset=44+8*(header[6]+header[7]+header[8]+header[9])
        if(offset+48!=calibration.size)return null
        bytes.position(offset)
        val local=FloatArray(12){bytes.float}
        val node=ByteBuffer.wrap(pose).order(ByteOrder.LITTLE_ENDIAN)
        val world=FloatArray(12){node.float}
        if((local.asList()+world.asList()).any{!it.isFinite()})return null
        // The third row of world-from-camera gives world-up in optical axes.
        val up=FloatArray(3){column ->(0..2).sumOf {row ->world[8+row].toDouble()*local[row*4+column]}.toFloat()}
        if(sqrt(up.sumOf {it.toDouble()*it}) !in .9..1.1)return null
        if(hypot(up[0],up[1])<.35f)return null // Looking almost straight up/down.
        val angle=(-PI/2-atan2(up[1].toDouble(),up[0].toDouble()))*180/PI
        return ((angle/90).roundToInt()*90%360+360)%360
    }
}
