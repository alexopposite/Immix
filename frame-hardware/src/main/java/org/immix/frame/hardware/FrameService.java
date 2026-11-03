package org.immix.frame.hardware;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.Display;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import org.immix.frame.FrameContract;
import org.immix.frame.IFrameHardware;
import org.immix.frame.IFrameListener;

/** Separate ordinary app: owner policy and IPC, with a manually launched privileged hardware adapter. */
public final class FrameService extends Service {
    static volatile FrameService instance;
    private HandlerThread thread;
    private Handler worker;
    private final SocketHardwareAdapter hardware = new SocketHardwareAdapter();
    private final RemoteCallbackList<IFrameListener> listeners = new RemoteCallbackList<>();
    private final Map<IBinder, Lease> leases = new HashMap<>();
    private final FramePolicy policy = new FramePolicy(SystemClock.elapsedRealtime());
    private volatile Bundle state = new Bundle();
    private final FrameController controller = new FrameController(hardware, hardware, policy);
    private boolean foreground;
    private String previousSignature = "";
    private final String providerInstance = java.util.UUID.randomUUID().toString();
    private long sequence;
    private void enforceClient() { enforceCallingOrSelfPermission(FrameContract.PERMISSION, "Frame hardware access requires authorization"); }
    private SharedPreferences preferences;
    private static final long MAX_LEASE = 60 * 60_000L;
    private final class Lease implements IBinder.DeathRecipient {
        final IBinder owner; final int uid; final long until;
        Lease(IBinder owner, int uid, long until) { this.owner = owner; this.uid = uid; this.until = until; }
        @Override public void binderDied() { worker.post(() -> removeLease(owner)); }
    }

    private final IFrameHardware.Stub binder = new IFrameHardware.Stub() {
        @Override public Bundle getState() { enforceClient(); return new Bundle(state); }
        @Override public void registerListener(IFrameListener listener) {
            enforceClient();
            if (listener != null) listeners.register(listener);
        }
        @Override public void unregisterListener(IFrameListener listener) {
            enforceClient();
            if (listener != null) listeners.unregister(listener);
        }
        @Override public void reportUserActivity() {
            enforceClient();
            worker.post(() -> policy.activity(SystemClock.elapsedRealtime()));
        }
        @Override public boolean requestPowerToggle() {
            enforceClient();
            if (!state.getBoolean(FrameContract.POWER_BUTTON_SUPPORTED, false)) return false;
            worker.post(() -> {
                boolean displayOn = ((PowerManager) getSystemService(POWER_SERVICE)).isInteractive();
                policy.togglePower(SystemClock.elapsedRealtime(), displayOn);
                pollSoon();
            });
            return true;
        }
        @Override public void offerLegacyTimeoutMinutes(int minutes) {
            enforceClient();
            // Migration is restricted to the legacy app, not every future display client.
            int caller = Binder.getCallingUid();
            try {
                if (getPackageManager().getApplicationInfo("nl.giejay.android.tv.immich", 0).uid != caller) return;
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { return; }
            worker.post(() -> {
                Long value = LegacyTimeoutMigration.candidate(preferences.contains("timeout_ms"), minutes);
                if (value != null) {
                    preferences.edit().putLong("timeout_ms", value).putString("timeout_source", "legacy_immix").apply();
                    policy.timeoutMillis = value;
                    pollSoon();
                }
            });
        }
        @Override public boolean acquireAwakeLease(IBinder owner, long durationMillis) {
            enforceClient();
            if (owner == null || durationMillis <= 0 || durationMillis > MAX_LEASE) return false;
            int uid = Binder.getCallingUid();
            synchronized (leases) {
                Lease previous = leases.get(owner);
                if (previous != null && previous.uid != uid) return false;
                int count = 0;
                for (Lease lease : leases.values()) if (lease.uid == uid) count++;
                if (previous == null && (count >= 8 || leases.size() >= 32)) return false;
                removeLease(owner);
                Lease lease = new Lease(owner, uid, SystemClock.elapsedRealtime() + durationMillis);
                try {
                    owner.linkToDeath(lease, 0);
                    leases.put(owner, lease);
                    return true;
                } catch (RemoteException ignored) { return false; }
            }
        }
        @Override public void releaseAwakeLease(IBinder owner) {
            enforceClient();
            int uid = Binder.getCallingUid();
            synchronized (leases) {
                Lease lease = leases.get(owner);
                if (lease != null && lease.uid == uid) removeLease(owner);
            }
        }

    };

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        state.putInt(FrameContract.VERSION_KEY, FrameContract.VERSION);
        state.putString(FrameContract.REASON, "starting");
        state.putString("provider_instance", providerInstance);
        state.putLong("state_sequence", sequence);
        preferences = getSharedPreferences("policy", MODE_PRIVATE);
        policy.timeoutMillis = preferences.getLong("timeout_ms", 15 * 60_000L);
        // A crash/restart always returns to observe-only; management is explicitly enabled by owner.
        thread = new HandlerThread("frame-hardware");
        thread.start();
        worker = new Handler(thread.getLooper());
        worker.post(tick);
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!foreground) {
            Notification.Builder builder;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                android.app.NotificationManager notifications = getSystemService(android.app.NotificationManager.class);
                notifications.createNotificationChannel(new android.app.NotificationChannel(
                    "frame-hardware", "Frame hardware", android.app.NotificationManager.IMPORTANCE_LOW));
                builder = new Notification.Builder(this, "frame-hardware");
            } else {
                builder = new Notification.Builder(this);
            }
            Notification notification = builder.setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Frame hardware monitor")
                .setContentText("Owner-controlled presence and display power service")
                .build();
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(1, notification);
            }
            foreground = true;
        }
        return START_STICKY;
    }
    private void removeLease(IBinder owner) {
        synchronized (leases) {
            Lease lease = leases.remove(owner);
            if (lease != null) owner.unlinkToDeath(lease, 0);
        }
    }
    private long leaseUntil(long now) {
        synchronized (leases) {
            long until = 0;
            for (IBinder token : leases.keySet().toArray(new IBinder[0])) {
                Lease lease = leases.get(token);
                if (lease.until <= now) removeLease(token); else until = Math.max(until, lease.until);
            }
            return until;
        }
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = SystemClock.elapsedRealtime();
            Calendar clock = Calendar.getInstance();
            policy.quietHours = FramePolicy.inQuietHours(clock.get(Calendar.HOUR_OF_DAY) * 60 + clock.get(Calendar.MINUTE),
                    preferences.getInt("quiet_start", -1), preferences.getInt("quiet_end", -1));
            long until = leaseUntil(now);
            FrameController.Snapshot sample = controller.tick(now, until);
            long lastMotion = sample.lastMotion;
            Bundle next = new Bundle();
            next.putInt(FrameContract.VERSION_KEY, FrameContract.VERSION);
            next.putBoolean(FrameContract.MANAGED, sample.managed);
            next.putBoolean(FrameContract.POWER_BUTTON_SUPPORTED, sample.managed && policy.canTogglePower());
            next.putLong("timeout_ms", policy.timeoutMillis);
            next.putString("timeout_source", preferences.getString("timeout_source", "default"));
            next.putBoolean(FrameContract.DISPLAY_ON, ((PowerManager) getSystemService(POWER_SERVICE)).isInteractive());
            next.putBoolean("presence_supported", sample.presenceSupported);
            next.putBoolean("power_control_supported", sample.powerSupported);
            next.putBoolean("deep_sleep_wake_supported", false);
            next.putBoolean("cpu_monitor_required", true);
            next.putLong("last_motion_elapsed_ms", lastMotion);
            next.putString(FrameContract.PRESENCE, lastMotion < 0 ? "unknown" :
                    sample.presenceSupported ? (now - lastMotion < 30_000L ? "recent_motion" : "no_recent_motion") : "unknown");
            next.putString(FrameContract.REASON, sample.error.isEmpty() ? policy.reason(now, until) : sample.error);
            Display display = ((DisplayManager) getSystemService(DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY);
            next.putInt("display_rotation", display == null ? -1 : display.getRotation());
            next.putString("mode", policy.enabled ? "management_requested" : "observe_only");
            String signature = next.toString();
            if (!signature.equals(previousSignature)) {
                previousSignature = signature;
                next.putString("provider_instance", providerInstance);
                next.putLong("state_sequence", ++sequence);
                state = next;
                int count = listeners.beginBroadcast();
                try { for (int i = 0; i < count; i++) try { listeners.getBroadcastItem(i).onStateChanged(new Bundle(next)); } catch (RemoteException ignored) {} }
                finally { listeners.finishBroadcast(); }
            }
            worker.postDelayed(this, 1000);
        }
    };

    // Only SettingsActivity in this APK can call these package-private owner operations.
    void configure(boolean enabled, long timeoutMillis, int quietStart, int quietEnd) {
        if (timeoutMillis < 0 || timeoutMillis > 24 * 60 * 60_000L ||
                quietStart < -1 || quietStart >= 1440 || quietEnd < -1 || quietEnd >= 1440 ||
                ((quietStart < 0) != (quietEnd < 0))) throw new IllegalArgumentException();
        worker.post(() -> {
            preferences.edit().putLong("timeout_ms", timeoutMillis).putString("timeout_source", "owner")
                    .putInt("quiet_start", quietStart).putInt("quiet_end", quietEnd).apply();
            policy.enabled = enabled; policy.manualOff = false; policy.timeoutMillis = timeoutMillis;
            policy.resetActivity(SystemClock.elapsedRealtime());
            pollSoon();
        });
    }
    void manualOff(boolean off) { worker.post(() -> {
        policy.manualOff = off;
        if (!off) policy.resetActivity(SystemClock.elapsedRealtime());
        pollSoon();
    }); }
    private void pollSoon() { worker.removeCallbacks(tick); worker.post(tick); }
    Bundle localState() { return new Bundle(state); }

    @Override protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
        Bundle snapshot = new Bundle(state);
        for (String key : new java.util.TreeSet<>(snapshot.keySet())) {
            writer.println(key + "=" + snapshot.get(key));
        }
        writer.println("listener_count=" + listeners.getRegisteredCallbackCount());
    }

    @Override public void onDestroy() {
        instance = null;
        worker.removeCallbacks(tick);
        worker.post(() -> {
            controller.relinquish();
            synchronized (leases) {
                for (IBinder token : leases.keySet().toArray(new IBinder[0])) removeLease(token);
            }
            listeners.kill();
            thread.quitSafely();
        });
        super.onDestroy();
    }
}
