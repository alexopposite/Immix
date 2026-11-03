package org.immix.frame.hardware;

/** Privilege boundary for display control. Implementations must fail explicitly when unsupported. */
public interface DisplayPower {
    boolean isInteractive() throws Exception;
    void setManaging(boolean enabled) throws Exception;
    /** Renew a bounded display hold while awake; false releases it without sleeping. */
    void setAwakeHold(boolean enabled) throws Exception;
    void wakeUp() throws Exception;
    void goToSleep() throws Exception;
}
