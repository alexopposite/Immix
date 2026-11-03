package org.immix.frame.hardware;

/** Hardware-independent activity source. A successful read is a fresh observation, not a cached event. */
public interface PresenceSensor {
    boolean isActivityDetected() throws Exception;
}
