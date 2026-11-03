package org.immix.frame.hardware;

import org.junit.Test;
import static org.junit.Assert.*;

public class FrameControllerTest {
    static final class Sensor implements PresenceSensor {
        boolean motion, broken;
        @Override public boolean isActivityDetected() throws Exception {
            if (broken) throw new Exception("Unavailable");
            return motion;
        }
    }
    static final class Power implements DisplayPower {
        boolean interactive = true, managing, held, failSleep, failWake, failHold, refuseManage;
        int holdRenewals;
        int sleeps, wakes;
        @Override public boolean isInteractive() { return interactive; }
        @Override public void setManaging(boolean value) throws Exception {
            if (value && refuseManage) throw new Exception("Denied");
            managing = value;
            if (!value) held = false;
        }
        @Override public void setAwakeHold(boolean enabled) throws Exception {
            if (failHold) throw new Exception("Denied");
            held = enabled;
            if (enabled) holdRenewals++;
        }
        @Override public void wakeUp() throws Exception {
            if (failWake) throw new Exception("Denied");
            interactive = true; wakes++;
        }
        @Override public void goToSleep() throws Exception {
            if (failSleep) throw new Exception("Denied");
            interactive = false; sleeps++;
        }
    }
    final Sensor sensor = new Sensor();
    final Power power = new Power();
    final FramePolicy policy = new FramePolicy(0);
    final FrameController controller = new FrameController(sensor, power, policy);
    public FrameControllerTest() { policy.timeoutMillis = 10000; }

    @Test public void displayHoldRenewsWithoutRepeatedWakeWhileScreenAlreadyOn() {
        policy.enabled = true;
        controller.tick(100, 0); controller.tick(200, 0);
        assertTrue(power.held);
        assertEquals(2, power.holdRenewals);
        assertEquals(0, power.wakes);
        controller.tick(10000, 0);
        assertFalse(power.held);
        assertFalse(power.interactive);
    }
    @Test public void alwaysOnRenewsBeyondTheOldTenMinuteLockLimit() {
        policy.enabled = true; policy.timeoutMillis = 0;
        controller.tick(100, 0); controller.tick(700000, 0);
        assertTrue(power.held);
        assertEquals(0, power.sleeps);
    }
    @Test public void observationNeverTakesPowerEvenAfterInactivity() {
        FrameController.Snapshot result = controller.tick(60000, 0);
        assertTrue(result.presenceSupported);
        assertFalse(result.managed);
        assertFalse(power.managing);
        assertEquals(0, power.sleeps);
    }
    @Test public void inactivitySleepsAndFreshMotionWakesAcrossDifferentAdapters() {
        policy.enabled = true;
        assertTrue(controller.tick(10000, 0).managed);
        assertEquals(1, power.sleeps);
        controller.tick(11000, 0);
        assertEquals(1, power.sleeps);
        sensor.motion = true;
        FrameController.Snapshot result = controller.tick(12000, 0);
        assertEquals(1, power.wakes);
        assertEquals(12000, result.lastMotion);
    }
    @Test public void remoteSleepIgnoresHeldSignalThenWakesOnNewMotion() {
        policy.enabled = true; sensor.motion = true;
        controller.tick(100, 0);
        assertTrue(policy.togglePower(200, true));
        controller.tick(300, 0);
        controller.tick(400, 0);
        assertFalse(power.interactive);
        assertEquals(0, power.wakes);
        sensor.motion = false; controller.tick(500, 0);
        sensor.motion = true; controller.tick(600, 0);
        assertTrue(power.interactive);
        assertEquals(1, power.wakes);
    }
    @Test public void ownerStayOffStillBlocksSensorAndLeases() {
        policy.enabled = true; policy.manualOff = true; sensor.motion = true;
        controller.tick(100, 999999);
        assertFalse(power.interactive);
        assertFalse(policy.togglePower(200, false));
        assertEquals("manual_off", policy.reason(300, 999999));
    }
    @Test public void quietHoursStillBlockMotionAndLease() {
        policy.enabled = true; policy.quietHours = true; sensor.motion = true;
        controller.tick(100, 999999);
        assertFalse(power.interactive);
    }
    @Test public void sensorFailureRelinquishesManagementAndRecoveryResamples() {
        policy.enabled = true; controller.tick(1, 0);
        assertTrue(power.managing);
        sensor.broken = true;
        FrameController.Snapshot missing = controller.tick(2, 0);
        assertFalse(missing.managed);
        assertFalse(missing.presenceSupported);
        assertFalse(power.managing);
        assertEquals("hardware_backend_unavailable", missing.error);
        sensor.broken = false; sensor.motion = true;
        assertTrue(controller.tick(3, 0).managed);
    }
    @Test public void deniedSleepIsReportedAsFailureAndReleasesManagement() {
        policy.enabled = true; power.failSleep = true;
        FrameController.Snapshot result = controller.tick(10000, 0);
        assertTrue(result.presenceSupported);
        assertFalse(result.powerSupported);
        assertFalse(result.managed);
        assertFalse(power.managing);
        assertEquals("power_control_unavailable", result.error);
    }
    @Test public void deniedDisplayHoldRelinquishesManagementBeforeWake() {
        policy.enabled = true; power.failHold = true; power.interactive = false;
        FrameController.Snapshot result = controller.tick(100, 0);
        assertFalse(result.managed);
        assertFalse(power.managing);
        assertEquals(0, power.wakes);
    }
    @Test public void deniedWakeRelinquishesPreviouslyAcquiredDisplayHold() {
        policy.enabled = true; power.failWake = true; power.interactive = false;
        FrameController.Snapshot result = controller.tick(100, 0);
        assertFalse(result.managed);
        assertFalse(power.held);
        assertFalse(power.managing);
        assertEquals("power_control_unavailable", result.error);
    }
    @Test public void managementDenialDoesNotAttemptDisplayChange() {
        policy.enabled = true; power.refuseManage = true;
        assertFalse(controller.tick(10000, 0).managed);
        assertEquals(0, power.sleeps);
    }
    @Test public void ownerDisablingManagementRelinquishesAndDoesNotSleep() {
        policy.enabled = true; controller.tick(1, 0);
        policy.enabled = false;
        controller.tick(10000, 0);
        assertFalse(power.managing);
        assertEquals(0, power.sleeps);
    }
    @Test public void expiredAppLeaseAllowsSleepAndNewUserActivityWakes() {
        policy.enabled = true;
        controller.tick(20000, 30000);
        assertTrue(power.interactive);
        controller.tick(30000, 30000);
        assertFalse(power.interactive);
        policy.activity(31000);
        controller.tick(31000, 0);
        assertTrue(power.interactive);
    }
}
