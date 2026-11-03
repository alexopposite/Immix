package org.immix.frame.hardware;
import org.junit.Test;
import static org.junit.Assert.*;
public class LegacyTimeoutMigrationTest {
    @Test public void importsNondefaultMinutes() { assertEquals(Long.valueOf(45 * 60000L), LegacyTimeoutMigration.candidate(false, 45)); }
    @Test public void importsAlwaysOn() { assertEquals(Long.valueOf(0), LegacyTimeoutMigration.candidate(false, 0)); }
    @Test public void priorImportOrOwnerSettingAlwaysWins() {
        assertNull(LegacyTimeoutMigration.candidate(true, 0));
        assertNull(LegacyTimeoutMigration.candidate(true, 45));
    }
    @Test public void rejectsOutOfRangeAndHandlesUpperBoundWithoutOverflow() {
        assertNull(LegacyTimeoutMigration.candidate(false, -1));
        assertNull(LegacyTimeoutMigration.candidate(false, Integer.MAX_VALUE));
        assertEquals(Long.valueOf(86400000), LegacyTimeoutMigration.candidate(false, 1440));
    }
}
