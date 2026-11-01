package org.immix.frame.hardware;

/** Pure elapsed-time policy. No Android dependencies, native calls, or wall-clock inactivity timers. */
public final class FramePolicy {
    public enum Decision { OBSERVE, AWAKE, ASLEEP }
    public boolean enabled;
    public boolean manualOff;
    public boolean quietHours;
    public long timeoutMillis = 15 * 60_000L;
    private long lastActivity;
    public FramePolicy(long now) { lastActivity = now; }
    public void activity(long now) { lastActivity = now; }
    public long lastActivity() { return lastActivity; }
    public static boolean inQuietHours(int now, int start, int end) {
        if (start < 0 || end < 0 || start == end) return false;
        return start < end ? now >= start && now < end : now >= start || now < end;
    }
    public Decision decide(long now, long leaseUntil) {
        if (!enabled) return Decision.OBSERVE;
        if (manualOff || quietHours) return Decision.ASLEEP;
        if (timeoutMillis == 0 || now < leaseUntil || now - lastActivity < timeoutMillis) return Decision.AWAKE;
        return Decision.ASLEEP;
    }
    public String reason(long now, long leaseUntil) {
        if (!enabled) return "observe_only";
        if (manualOff) return "manual_off";
        if (quietHours) return "quiet_hours";
        if (timeoutMillis == 0) return "always_on";
        if (now < leaseUntil) return "app_lease";
        return decide(now, leaseUntil) == Decision.AWAKE ? "recent_activity" : "inactivity";
    }
}
