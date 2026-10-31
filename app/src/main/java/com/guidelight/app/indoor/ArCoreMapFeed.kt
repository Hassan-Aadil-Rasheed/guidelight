package com.guidelight.app.indoor

import android.content.Context
import android.media.Image
import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.*
import com.google.ar.core.exceptions.NotYetAvailableException
import com.introlab.rtabmap.RTABMapLib as Native
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import com.guidelight.app.vision.AiSnapshot
import com.guidelight.app.vision.AiImageQuality

/** ARCore Java camera -> RTAB-Map's documented external-camera driver (3).
 * Same coordinate/texture contract as upstream ARCoreSharedCamera, with explicit
 * YUV stride packing so padded or planar device images cannot silently disappear.
 * Image/point-cloud ownership ends after the synchronous native copy, on the GL thread.
 */
class ArCoreMapFeed(context: Context) : AutoCloseable {
    private val snapshotRequest=AtomicReference<CompletableDeferred<AiSnapshot>?>(null)
    private val sensorRotation=context.getSystemService(android.hardware.camera2.CameraManager::class.java)
        .getCameraCharacteristics("0").get(android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
    private val session = Session(context).apply {
        configure(Config(this).apply {
            focusMode = Config.FocusMode.AUTO
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            planeFindingMode = Config.PlaneFindingMode.DISABLED
            lightEstimationMode = Config.LightEstimationMode.DISABLED
            depthMode = Config.DepthMode.DISABLED
        })
    }
    val frames = AtomicInteger()
    val sentFrames = AtomicInteger()
    /** Keep AR camera/preview alive while a stationary AI request owns the CPU. */
    @Volatile var inferencePaused = false
    @Volatile var tracked = false
        private set
    @Volatile var trackingMessage = "Starting camera…"
        private set
    private var texture = 0
    private var lastTimestamp = 0L
    private var lastSent = 0L
    private var lastPreview = 0L
    internal var previewImageWidth = 0
        private set
    internal var previewImageHeight = 0
        private set
    private val previewTextures=IntArray(2)
    private var y = ByteBuffer.allocateDirect(0)
    private var vu = ByteBuffer.allocateDirect(0)
    private var geometry = Triple(-1,-1,-1)
    private val quad = floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f)
    private val uv = FloatArray(8)
    private val projection = FloatArray(16)
    private val previewUv = FloatArray(8)
    private val vertices = buffer(quad)
    private val textureVertices = buffer(FloatArray(8))
    private var previewProgram = 0
    fun resume() = session.resume()

    fun update(application: Long, width: Int, height: Int, rotation: Int) {
        if (width <= 0 || height <= 0) return
        if (texture == 0) {
            val names = IntArray(1); GLES20.glGenTextures(1,names,0); texture = names[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            session.setCameraTextureName(texture)
        }
        val size = Triple(rotation,width,height)
        if (geometry != size) { session.setDisplayGeometry(rotation,width,height); geometry = size }
        val frame = session.update()
        if (frame.timestamp == 0L || frame.timestamp == lastTimestamp) return
        lastTimestamp = frame.timestamp; frames.incrementAndGet()
        val camera = frame.camera
        tracked = camera.trackingState == TrackingState.TRACKING
        trackingMessage = if (tracked) "Camera tracking ready" else when (camera.trackingFailureReason) {
            TrackingFailureReason.INSUFFICIENT_LIGHT -> "More light needed. Face a well-lit room."
            TrackingFailureReason.EXCESSIVE_MOTION -> "Move the phone more slowly."
            TrackingFailureReason.INSUFFICIENT_FEATURES -> "Face a doorway or textured furniture and slowly move sideways."
            else -> "Finding room details. Slowly move the phone sideways."
        }
        // Preview the same full CPU image used by snapshots, not the narrower GPU
        // texture. On the OPPO these streams are 640x480 and 1920x1080 respectively.
        if(frame.timestamp-lastPreview<80_000_000L && snapshotRequest.get()==null)return
        try {
            frame.acquireCameraImage().use { image ->
                pack(image)
                uploadPreview(image.width,image.height,(sensorRotation-rotation*90+360)%360)
                lastPreview=frame.timestamp
                snapshotRequest.getAndSet(null)?.let { request ->
                  try {
                    val data=ByteArray(image.width*image.height*3/2)
                    y.duplicate().get(data,0,image.width*image.height)
                    vu.duplicate().get(data,image.width*image.height,image.width*image.height/2)
                    check(AiImageQuality.usableLuma(data,image.width*image.height)) { "The camera view is too dark, bright, or featureless. Face a well-lit room and retry." }
                    request.complete(AiSnapshot(data,image.width,image.height,(sensorRotation-rotation*90+360)%360,
                        android.os.SystemClock.elapsedRealtime(),tracked))
                  } catch(error: Exception) { request.completeExceptionally(error) }
                }
                if(inferencePaused || !tracked || frame.timestamp-lastSent<80_000_000L)return
                frame.acquirePointCloud().use { cloud ->
                val pose = camera.pose
                val view = camera.displayOrientedPose.inverse()
                val fl = camera.imageIntrinsics.focalLength; val pp = camera.imageIntrinsics.principalPoint
                frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                    quad, Coordinates2d.IMAGE_NORMALIZED, uv)
                camera.getProjectionMatrix(projection,0,.1f,100f)
                val u = vu.duplicate().apply { position(1) }.slice()
                val points = cloud.points
                Native.postOdometryEvent(application, pose.tx(),pose.ty(),pose.tz(),pose.qx(),pose.qy(),pose.qz(),pose.qw(),
                    fl[0],fl[1],pp[0],pp[1], 0f,0f,0f,0f,0f,0f,1f,
                    image.timestamp / 1_000_000_000.0, y,u,vu, image.width*image.height,image.width,image.height,image.format,
                    points,points.remaining()/4, view.tx(),view.ty(),view.tz(),view.qx(),view.qy(),view.qz(),view.qw(),
                    projection[0],projection[5],projection[8],projection[9],projection[10],projection[11],projection[14],
                    uv[0],uv[1],uv[2],uv[3],uv[4],uv[5],uv[6],uv[7])
                lastSent = frame.timestamp; sentFrames.incrementAndGet()
                }
            }
        } catch (_: NotYetAvailableException) { /* Wait for the next actual camera image. */ }
    }

    private fun uploadPreview(width: Int,height: Int,rotation: Int) {
        if(previewTextures[0]==0)GLES20.glGenTextures(2,previewTextures,0)
        val resized=previewImageWidth!=width || previewImageHeight!=height
        listOf(y.duplicate(),vu.duplicate()).forEachIndexed { index,data ->
            val format=if(index==0)GLES20.GL_LUMINANCE else GLES20.GL_LUMINANCE_ALPHA
            val w=if(index==0)width else width/2;val h=if(index==0)height else height/2
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0+index)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,previewTextures[index])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT,1)
            if(resized)GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,format,w,h,0,format,GLES20.GL_UNSIGNED_BYTE,data)
            else GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,w,h,format,GLES20.GL_UNSIGNED_BYTE,data)
        }
        previewImageWidth=width;previewImageHeight=height
        // OpenGL bottom-left -> upright image bottom-left -> inverse sensor rotation.
        val corners=floatArrayOf(0f,1f,1f,1f,0f,0f,1f,0f)
        android.graphics.Matrix().apply {setRotate(-rotation.toFloat(),.5f,.5f)}.mapPoints(corners)
        textureVertices.clear();textureVertices.put(corners).position(0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    suspend fun snapshot(): AiSnapshot {
        val request=CompletableDeferred<AiSnapshot>()
        check(snapshotRequest.compareAndSet(null,request)) { "A camera capture is already pending." }
        try { return withTimeout(5_000) {request.await()} }
        finally {snapshotRequest.compareAndSet(request,null)}
    }

    /** Show the real camera even before visual tracking has initialized. */
    fun drawPreview(width: Int, height: Int) {
        if (previewImageWidth == 0 || previewTextures[0] == 0) return
        if (previewProgram == 0) {
            fun shader(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shader ->
                GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader)
                val status = IntArray(1); GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
                check(status[0] != 0) { "Camera preview shader could not compile." }
            }
            val vertex = shader(GLES20.GL_VERTEX_SHADER, "attribute vec2 p; attribute vec2 uv; varying vec2 t; void main(){ gl_Position=vec4(p,0.,1.); t=uv; }")
            val fragment = shader(GLES20.GL_FRAGMENT_SHADER, "precision mediump float; uniform sampler2D yPlane; uniform sampler2D vuPlane; varying vec2 t; void main(){ float y=1.1643*(texture2D(yPlane,t).r-0.0625); vec4 vu=texture2D(vuPlane,t); float v=vu.r-0.5; float u=vu.a-0.5; gl_FragColor=vec4(y+1.5958*v,y-0.39173*u-0.81290*v,y+2.017*u,1.0); }")
            previewProgram = GLES20.glCreateProgram()
            GLES20.glAttachShader(previewProgram,vertex); GLES20.glAttachShader(previewProgram,fragment)
            GLES20.glLinkProgram(previewProgram)
            val linked = IntArray(1); GLES20.glGetProgramiv(previewProgram,GLES20.GL_LINK_STATUS,linked,0)
            check(linked[0] != 0) { "Camera preview could not start." }
            GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
        }
        GLES20.glViewport(0,0,width,height)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(previewProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,previewTextures[0])
        GLES20.glUniform1i(GLES20.glGetUniformLocation(previewProgram,"yPlane"),0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,previewTextures[1])
        GLES20.glUniform1i(GLES20.glGetUniformLocation(previewProgram,"vuPlane"),1)
        val p = GLES20.glGetAttribLocation(previewProgram,"p")
        val t = GLES20.glGetAttribLocation(previewProgram,"uv")
        GLES20.glEnableVertexAttribArray(p); GLES20.glEnableVertexAttribArray(t)
        GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,0,vertices)
        GLES20.glVertexAttribPointer(t,2,GLES20.GL_FLOAT,false,0,textureVertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
        GLES20.glDisableVertexAttribArray(p); GLES20.glDisableVertexAttribArray(t)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0)
        GLES20.glUseProgram(0); GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun buffer(values: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(values.size*4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }

    private fun pack(image: Image) {
        val width = image.width; val height = image.height
        if (y.capacity() != width*height) {
            y = ByteBuffer.allocateDirect(width*height)
            vu = ByteBuffer.allocateDirect(width*height/2)
        }
        y.clear(); vu.clear()
        val yp = image.planes[0]; val up = image.planes[1]; val vp = image.planes[2]
        val sourceY = yp.buffer.duplicate(); val yStart = sourceY.position()
        if (yp.pixelStride == 1 && yp.rowStride == width) {
            sourceY.limit(yStart+width*height); y.put(sourceY)
        } else {
            for (row in 0 until height) for (column in 0 until width)
                y.put(sourceY.get(yStart+row*yp.rowStride+column*yp.pixelStride))
        }
        val sourceU = up.buffer.duplicate(); val sourceV = vp.buffer.duplicate()
        val uStart = sourceU.position(); val vStart = sourceV.position()
        for (row in 0 until height/2) for (column in 0 until width/2) {
            vu.put(sourceV.get(vStart+row*vp.rowStride+column*vp.pixelStride))
            vu.put(sourceU.get(uStart+row*up.rowStride+column*up.pixelStride))
        }
        y.flip(); vu.flip()
    }

    override fun close() {
        snapshotRequest.getAndSet(null)?.cancel()
        session.pause(); session.close()
    }
}
