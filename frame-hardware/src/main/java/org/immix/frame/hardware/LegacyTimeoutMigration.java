package org.immix.frame.hardware;

/** Only seed a fresh helper; never overwrite a prior import or an owner-selected timeout. */
public final class LegacyTimeoutMigration {
    private LegacyTimeoutMigration() {}
    public static Long candidate(boolean hasTimeout, int minutes) {
        if (hasTimeout || minutes < 0 || minutes > 1440) return null;
        return minutes * 60_000L;
    }
}
