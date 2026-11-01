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
    private final BackendClient backend = new BackendClient();
    private final RemoteCallbackList<IFrameListener> listeners = new RemoteCallbackList<>();
    private final Map<IBinder, Lease> leases = new HashMap<>();
    private final FramePolicy policy = new FramePolicy(SystemClock.elapsedRealtime());
    private volatile Bundle state = new Bundle();
    private long lastMotion = -1;
    private boolean nativeAvailable;
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
            String error = "";
            nativeAvailable = false;
            boolean interactive = ((PowerManager) getSystemService(POWER_SERVICE)).isInteractive();
            try {
                String[] sample = backend.command("POLL").split(" ");
                if (sample.length != 3) throw new IllegalStateException();
                nativeAvailable = true;
                if ("1".equals(sample[1])) { lastMotion = now; policy.activity(now); }
                interactive = "1".equals(sample[2]);
            } catch (Exception e) { error = "hardware_backend_unavailable"; }
            Calendar clock = Calendar.getInstance();
            policy.quietHours = FramePolicy.inQuietHours(clock.get(Calendar.HOUR_OF_DAY) * 60 + clock.get(Calendar.MINUTE),
                    preferences.getInt("quiet_start", -1), preferences.getInt("quiet_end", -1));
            long until = leaseUntil(now);
            FramePolicy.Decision decision = policy.decide(now, until);
            boolean managed = false;
            if (nativeAvailable) {
                try {
                    backend.command(policy.enabled ? "MANAGE" : "OBSERVE");
                    if (policy.enabled) {
                        managed = true;
                        if (decision == FramePolicy.Decision.AWAKE && !interactive) backend.command("WAKE");
                        if (decision == FramePolicy.Decision.ASLEEP && interactive) backend.command("SLEEP");
                    }
                } catch (Exception e) { error = "power_control_unavailable"; managed = false; }
            }
            Bundle next = new Bundle();
            next.putInt(FrameContract.VERSION_KEY, FrameContract.VERSION);
            next.putBoolean(FrameContract.MANAGED, managed);
            next.putBoolean(FrameContract.DISPLAY_ON, ((PowerManager) getSystemService(POWER_SERVICE)).isInteractive());
            next.putBoolean("presence_supported", nativeAvailable);
            next.putBoolean("power_control_supported", nativeAvailable && error.isEmpty());
            next.putBoolean("deep_sleep_wake_supported", false);
            next.putBoolean("cpu_monitor_required", true);
            next.putLong("last_motion_elapsed_ms", lastMotion);
            next.putString(FrameContract.PRESENCE, lastMotion < 0 ? "unknown" :
                    now - lastMotion < 30_000L ? "recent_motion" : "no_recent_motion");
            next.putString(FrameContract.REASON, error.isEmpty() ? policy.reason(now, until) : error);
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
        preferences.edit().putLong("timeout_ms", timeoutMillis).putInt("quiet_start", quietStart).putInt("quiet_end", quietEnd).apply();
        worker.post(() -> {
            policy.enabled = enabled; policy.manualOff = false; policy.timeoutMillis = timeoutMillis;
            policy.activity(SystemClock.elapsedRealtime());
        });
    }
    void manualOff(boolean off) { worker.post(() -> policy.manualOff = off); }
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
            try { backend.command("OBSERVE"); } catch (Exception ignored) {}
            synchronized (leases) {
                for (IBinder token : leases.keySet().toArray(new IBinder[0])) removeLease(token);
            }
            listeners.kill();
            thread.quitSafely();
        });
        super.onDestroy();
    }
}
