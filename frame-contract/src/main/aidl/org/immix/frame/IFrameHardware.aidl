package org.immix.frame;
import android.os.Bundle;
import android.os.IBinder;
import org.immix.frame.IFrameListener;
interface IFrameHardware {
    Bundle getState();
    void registerListener(IFrameListener listener);
    void unregisterListener(IFrameListener listener);
    void reportUserActivity();
    boolean acquireAwakeLease(IBinder owner, long durationMillis);
    void releaseAwakeLease(IBinder owner);
    boolean requestPowerToggle();
    void offerLegacyTimeoutMinutes(int minutes);
}
