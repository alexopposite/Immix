package com.kitesystems.nix.frame;

/** Exact firmware JNI identity; keep this confined to the hardware adapter. */
public final class MotionSensor {
    static { System.loadLibrary("gpio_jni"); }
    public native int readMotionSensor();
    public native boolean readMotionSensorPower();
    public native void setMotionSensorPower(boolean enabled);
    public native int setWakeOnMotion(boolean enabled);
}
