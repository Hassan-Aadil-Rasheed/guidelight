package com.guidelight.app.indoor

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SavedRoomOrientationTest {
    private val identity=floatArrayOf(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f)
    private val optical=floatArrayOf(0f,0f,1f,0f,-1f,0f,0f,0f,0f,-1f,0f,0f)
    private fun pose(values:FloatArray=identity)=ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {values.forEach {putFloat(it)}}.array()
    private fun calibration(local:FloatArray=optical,width:Int=240,height:Int=320)=ByteBuffer.allocate(164).order(ByteOrder.LITTLE_ENDIAN).apply {
        intArrayOf(0,24,2,0,width,height,9,0,0,0,12).forEach {putInt(it)}
        repeat(9){putDouble(if(it%4==0)1.0 else 0.0)}
        local.forEach {putFloat(it)}
    }.array()
    @Test fun recordedOpticalUpDeterminesAllFourRotations() {
        val rotations=listOf(
            optical,
            floatArrayOf(0f,0f,1f,0f,0f,1f,0f,0f,-1f,0f,0f,0f),
            floatArrayOf(0f,0f,1f,0f,1f,0f,0f,0f,0f,1f,0f,0f),
            floatArrayOf(0f,0f,1f,0f,0f,-1f,0f,0f,1f,0f,0f,0f))
        assertEquals(listOf(0,90,180,270),rotations.map {SavedRoomOrientation.rotation(pose(),calibration(it))})
    }
    @Test fun pixelAspectRatioDoesNotDetermineOrientation() {
        assertEquals(0,SavedRoomOrientation.rotation(pose(),calibration(width=640,height=480)))
        assertEquals(0,SavedRoomOrientation.rotation(pose(),calibration(width=240,height=320)))
    }
    @Test fun mapPoseAndCalibrationTogetherCorrectTheSavedPortraitRegression() {
        val recorded=floatArrayOf(.8430836f,-.2698916f,-.4651542f,0f,-.2603135f,-.9616717f,.0861672f,0f,-.4705814f,.0484398f,-.8810261f,0f)
        val local=floatArrayOf(0f,0f,1f,0f,0f,-1f,0f,0f,1f,0f,0f,0f)
        assertEquals(90,SavedRoomOrientation.rotation(pose(recorded),calibration(local)))
    }
    @Test fun malformedOrMissingOrientationIsNotGuessed() {
        assertNull(SavedRoomOrientation.rotation(null,calibration()))
        assertNull(SavedRoomOrientation.rotation(pose(),calibration().copyOf(163)))
        assertNull(SavedRoomOrientation.rotation(pose(),calibration().apply {this[12]=1}))
        assertNull(SavedRoomOrientation.rotation(pose(identity.copyOf().apply {this[0]=Float.NaN}),calibration()))
    }
    @Test fun viewsWithoutAProjectedUpDirectionAreRejected() {
        assertNull(SavedRoomOrientation.rotation(pose(),calibration(identity)))
    }
}
