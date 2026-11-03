package org.immix.frame;

import android.content.ComponentName;

/** Version 2 protocol: unknown Bundle fields must be ignored; missing capabilities are false. */
public final class FrameContract {
    private FrameContract() {}
    public static final int VERSION = 2;
    public static final String PERMISSION = "org.immix.frame.permission.BIND_HARDWARE";
    public static final String PACKAGE = "org.immix.frame.hardware";
    public static final ComponentName SERVICE = new ComponentName(PACKAGE, PACKAGE + ".FrameService");
    public static final ComponentName SETTINGS = new ComponentName(PACKAGE, PACKAGE + ".SettingsActivity");
    public static final String VERSION_KEY = "protocol_version";
    public static final String POWER_BUTTON_SUPPORTED = "power_button_supported";
    public static final String MANAGED = "power_policy_active";
    public static final String DISPLAY_ON = "display_on";
    public static final String PRESENCE = "presence";
    public static final String REASON = "reason";
}
