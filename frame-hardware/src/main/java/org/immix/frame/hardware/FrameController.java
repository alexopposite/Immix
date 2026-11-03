package org.immix.frame.hardware;

/** Testable sensor -> policy -> power orchestration; no Android, socket or JNI dependencies. */
public final class FrameController {
    private final PresenceSensor sensor;
    private final DisplayPower power;
    private final FramePolicy policy;
    private long lastMotion = -1;
    public FrameController(PresenceSensor sensor, DisplayPower power, FramePolicy policy) {
        this.sensor = sensor; this.power = power; this.policy = policy;
    }
    public static final class Snapshot {
        public final boolean presenceSupported, powerSupported, managed;
        public final long lastMotion;
        public final String error;
        Snapshot(boolean presence, boolean power, boolean managed, long lastMotion, String error) {
            this.presenceSupported = presence; this.powerSupported = power; this.managed = managed;
            this.lastMotion = lastMotion; this.error = error;
        }
    }
    public Snapshot tick(long now, long leaseUntil) {
        final boolean detected, interactive;
        try {
            detected = sensor.isActivityDetected();
            interactive = power.isInteractive();
        } catch (Exception error) {
            relinquish();
            return new Snapshot(false, false, false, lastMotion, "hardware_backend_unavailable");
        }
        if (detected) lastMotion = now;
        policy.motion(now, detected);
        try {
            power.setManaging(policy.enabled);
            if (policy.enabled) {
                FramePolicy.Decision decision = policy.decide(now, leaseUntil);
                power.setAwakeHold(decision == FramePolicy.Decision.AWAKE);
                if (decision == FramePolicy.Decision.AWAKE && !interactive) power.wakeUp();
                else if (decision == FramePolicy.Decision.ASLEEP && interactive) power.goToSleep();
            }
            return new Snapshot(true, true, policy.enabled, lastMotion, "");
        } catch (Exception error) {
            relinquish();
            return new Snapshot(true, false, false, lastMotion, "power_control_unavailable");
        }
    }
    public void relinquish() { try { power.setManaging(false); } catch (Exception ignored) {} }
}
