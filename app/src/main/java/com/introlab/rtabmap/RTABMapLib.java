package com.introlab.rtabmap;

import android.app.Activity;
import android.content.Context;
import android.os.IBinder;

/** JNI names are the unmodified upstream engine ABI. See assets/mapping/NOTICES.txt. */
public final class RTABMapLib {
    static { System.loadLibrary("NativeRTABMap"); }
    private RTABMapLib() {}
    public static native long createNativeApplication(Activity activity);
    public static native void destroyNativeApplication(long application);
    public static native boolean isBuiltWith(long application, int driver);
    public static native void setScreenRotation(long application, int displayRotation, int cameraRotation);
    public static native int openDatabase(long application, String path, boolean inMemory, boolean optimize, boolean clear);
    public static native boolean startCamera(long application, IBinder binder, Context context, Activity activity, int driver);
    public static native void stopCamera(long application);
    public static native void initGlContent(long application);
    public static native void setupGraphic(long application, int width, int height);
    public static native int render(long application);
    public static native void setCamera(long application, int index);
    public static native void setLocalizationMode(long application, boolean enabled);
    public static native void setAppendMode(long application, boolean enabled);
    public static native void setPausedMapping(long application, boolean paused);
    public static native void setMapCloudShown(long application, boolean enabled);
    public static native void setOdomCloudShown(long application, boolean enabled);
    public static native void setGraphVisible(long application, boolean enabled);
    public static native void setGridVisible(long application, boolean enabled);
    public static native int setMappingParameter(long application, String key, String value);
    public static native void save(long application, String path);
    public static native void postOdometryEvent(long application,
        float x, float y, float z, float qx, float qy, float qz, float qw,
        float fx, float fy, float cx, float cy,
        float rx, float ry, float rz, float rqx, float rqy, float rqz, float rqw,
        double stamp, java.nio.ByteBuffer yPlane, java.nio.ByteBuffer uPlane, java.nio.ByteBuffer vPlane,
        int yLength, int width, int height, int format, java.nio.FloatBuffer points, int pointCount,
        float vx, float vy, float vz, float vqx, float vqy, float vqz, float vqw,
        float p00, float p11, float p02, float p12, float p22, float p32, float p23,
        float t0, float t1, float t2, float t3, float t4, float t5, float t6, float t7);
}
