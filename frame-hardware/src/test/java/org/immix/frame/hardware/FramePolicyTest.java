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
    @Test public void powerButtonSleepsThenSecondPressWakesAndResetsTimeout() {
        FramePolicy p = enabled();
        assertTrue(p.togglePower(2000, true));
        assertEquals("remote_sleep", p.reason(2001, 999999));
        assertEquals(FramePolicy.Decision.ASLEEP, p.decide(2001, 999999));
        assertTrue(p.togglePower(3000, false));
        assertEquals(FramePolicy.Decision.AWAKE, p.decide(12999, 0));
        assertEquals(FramePolicy.Decision.ASLEEP, p.decide(13000, 0));
    }
    @Test public void powerButtonWakesAfterNaturalInactivity() {
        FramePolicy p = enabled();
        assertEquals(FramePolicy.Decision.ASLEEP, p.decide(20000, 0));
        assertTrue(p.togglePower(20000, false));
        assertEquals(FramePolicy.Decision.AWAKE, p.decide(20000, 0));
    }
    @Test public void regularUserActivityClearsRemoteSleep() {
        FramePolicy p = enabled();
        p.togglePower(2000, true);
        p.activity(3000);
        assertEquals(FramePolicy.Decision.AWAKE, p.decide(3001, 0));
    }
    @Test public void observationAndQuietHoursDoNotAcceptPowerToggle() {
        FramePolicy p = new FramePolicy(0);
        assertFalse(p.togglePower(10, true));
        p.enabled = true; p.quietHours = true;
        assertFalse(p.togglePower(20, false));
    }
    @Test public void alwaysOnStillAllowsRemoteSleepAndMotionWake() {
        FramePolicy p = enabled(); p.timeoutMillis = 0;
        p.togglePower(2000, true);
        assertEquals(FramePolicy.Decision.ASLEEP, p.decide(3000, 0));
        p.motion(4000, true);
        assertEquals(FramePolicy.Decision.AWAKE, p.decide(4000, 0));
    }
    @Test public void explicitOwnerResetClearsRemoteSleep() {
        FramePolicy p = enabled(); p.togglePower(2000, true);
        p.resetActivity(3000);
        assertEquals(FramePolicy.Decision.AWAKE, p.decide(3000, 0));
    }
}
