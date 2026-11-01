package org.immix.frame;

/** Reject stale callbacks after reconnect or a newer synchronous snapshot. */
public final class StateCursor {
    private String provider;
    private long sequence = -1;
    public void reset(String provider) { this.provider = provider; sequence = -1; }
    public boolean accept(String provider, long sequence) {
        if (this.provider == null || !this.provider.equals(provider) || sequence <= this.sequence) return false;
        this.sequence = sequence;
        return true;
    }
}
