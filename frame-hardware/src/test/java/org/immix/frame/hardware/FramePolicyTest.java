package org.immix.frame.hardware;

import org.junit.Test;
import static org.junit.Assert.*;

public class FramePolicyTest {
    private FramePolicy enabled() {
        FramePolicy policy = new FramePolicy(1000);
        policy.enabled = true;
        policy.timeoutMillis = 10000;
        return policy;
    }
    @Test public void overnightQuietHoursHandleMidnightAndExclusiveEnd() {
        assertTrue(FramePolicy.inQuietHours(23 * 60, 22 * 60, 7 * 60));
        assertTrue(FramePolicy.inQuietHours(6 * 60, 22 * 60, 7 * 60));
        assertFalse(FramePolicy.inQuietHours(7 * 60, 22 * 60, 7 * 60));
        assertFalse(FramePolicy.inQuietHours(12 * 60, 22 * 60, 7 * 60));
    }
    @Test public void disabledAndEqualQuietHoursDoNotBlockWake() {
        assertFalse(FramePolicy.inQuietHours(100, -1, -1));
        assertFalse(FramePolicy.inQuietHours(100, 100, 100));
    }
    @Test public void daytimeQuietHoursUseInclusiveStart() {
        assertTrue(FramePolicy.inQuietHours(100, 100, 200));
        assertFalse(FramePolicy.inQuietHours(200, 100, 200));
    }
    @Test public void defaultsToObserveOnly() {
        FramePolicy p = new FramePolicy(0);
        p.manualOff = true;
        assertEquals(FramePolicy.Decision.OBSERVE, p.decide(100000, 0));
    }
    @Test public void inactivityUsesElapsedTimeNotPollingTicks() {
        FramePolicy p = enabled();
        assertEquals(FramePolicy.Decision.AWAKE, p.decide(10999, 0));
        assertEquals(FramePolicy.Decision.ASLEEP, p.decide(11000, 0));
        assertEquals(FramePolicy.Decision.ASLEEP, p.decide(999999, 0));
    }
    @Test public void motionOrRemoteActivityResetsTimeout() {
        FramePolicy p = enabled();
        p.activity(50000);
        assertEquals(FramePolicy.Decision.AWAKE, p.decide(50001, 0));
    }
    @Test public void manualOffDefeatsMotionAndAppLease() {
        FramePolicy p = enabled();
        p.manualOff = true;
        p.activity(90000);
        assertEquals(FramePolicy.Decision.ASLEEP, p.decide(90001, 999999));
        assertEquals("manual_off", p.reason(90001, 999999));
    }
    @Test public void quietHoursDefeatAppLease() {
        FramePolicy p = enabled();
        p.quietHours = true;
        assertEquals(FramePolicy.Decision.ASLEEP, p.decide(2000, 999999));
    }
    @Test public void boundedLeaseDefersInactivityOnlyUntilExpiry() {
        FramePolicy p = enabled();
        assertEquals(FramePolicy.Decision.AWAKE, p.decide(20000, 30000));
        assertEquals(FramePolicy.Decision.ASLEEP, p.decide(30000, 30000));
    }
    @Test public void alwaysOnStillRespectsOwnerOff() {
        FramePolicy p = enabled();
        p.timeoutMillis = 0;
        assertEquals(FramePolicy.Decision.AWAKE, p.decide(999999, 0));
        p.manualOff = true;
        assertEquals(FramePolicy.Decision.ASLEEP, p.decide(999999, 0));
    }
}
