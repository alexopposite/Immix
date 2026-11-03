package org.immix.frame;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

/** Event-driven display client. Never starts a privileged backend or changes owner policy. */
public final class FrameHardwareClient implements AutoCloseable {
    public interface Observer { void onState(Bundle state); }
    private final Context context;
    private final Observer observer;
    private final Integer legacyTimeoutMinutes;
    private boolean powerButtonAllowed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private IFrameHardware remote;
    private boolean bound;
    private boolean closed;
    private final StateCursor cursor = new StateCursor();
    private final Runnable reconnect = this::connect;
    private final IFrameListener listener = new IFrameListener.Stub() {
        @Override public void onStateChanged(Bundle state) {
            main.post(() -> {
                if (!closed && cursor.accept(state.getString("provider_instance"), state.getLong("state_sequence", -1))) publish(state);
            });
        }
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed) return;
            remote = IFrameHardware.Stub.asInterface(binder);
            try {
                remote.registerListener(listener);
                Bundle state = remote.getState();
                if (state.getInt(FrameContract.VERSION_KEY, 0) != FrameContract.VERSION) {
                    disconnect();
                    main.postDelayed(reconnect, 5000);
                    return;
                }
                if (legacyTimeoutMinutes != null) remote.offerLegacyTimeoutMinutes(legacyTimeoutMinutes);
                cursor.reset(state.getString("provider_instance"));
                if (cursor.accept(state.getString("provider_instance"), state.getLong("state_sequence", -1))) publish(state);
            } catch (RemoteException | SecurityException e) { disconnected(); }
        }
        @Override public void onServiceDisconnected(ComponentName name) { disconnected(); }
        @Override public void onBindingDied(ComponentName name) { retryBinding(); }
        @Override public void onNullBinding(ComponentName name) { retryBinding(); }
    };
    public FrameHardwareClient(Context context, Observer observer) { this(context, observer, null); }
    public FrameHardwareClient(Context context, Observer observer, Integer legacyTimeoutMinutes) {
        this.legacyTimeoutMinutes = legacyTimeoutMinutes;
        this.context = context.getApplicationContext();
        this.observer = observer;
    }
    public void connect() {
        if (closed || bound) return;
        try { bound = context.bindService(new Intent().setComponent(FrameContract.SERVICE),
                connection, Context.BIND_AUTO_CREATE); }
        catch (SecurityException e) { bound = false; }
        if (!bound) main.postDelayed(reconnect, 5000);
    }
    private void retryBinding() {
        disconnect();
        if (!closed) main.postDelayed(reconnect, 5000);
    }
    private void disconnected() {
        cursor.reset(null);
        remote = null;
        publish(new Bundle());
        // Android reconnects an existing service binding after service process death.
    }
    public void reportUserActivity() {
        if (remote != null) try { remote.reportUserActivity(); }
        catch (RemoteException | SecurityException ignored) { disconnected(); }
    }
    private void publish(Bundle state) {
        powerButtonAllowed = state.getBoolean(FrameContract.MANAGED, false) &&
                state.getBoolean(FrameContract.POWER_BUTTON_SUPPORTED, false);
        observer.onState(state);
    }
    /** Consume repeats without issuing a second toggle; fall through when capability is absent. */
    public boolean requestPowerToggle(int repeatCount) {
        if (!powerButtonAllowed || remote == null) return false;
        if (repeatCount > 0) return true;
        try { return remote.requestPowerToggle(); }
        catch (RemoteException | SecurityException ignored) { disconnected(); return false; }
    }
    public boolean isManaged(Bundle state) { return state.getBoolean(FrameContract.MANAGED, false); }
    private void disconnect() {
        if (remote != null) try { remote.unregisterListener(listener); } catch (RemoteException | SecurityException ignored) {}
        if (bound) { context.unbindService(connection); bound = false; }
        remote = null;
        cursor.reset(null);
        publish(new Bundle());
    }
    @Override public void close() {
        closed = true;
        main.removeCallbacks(reconnect);
        disconnect();
    }
}
