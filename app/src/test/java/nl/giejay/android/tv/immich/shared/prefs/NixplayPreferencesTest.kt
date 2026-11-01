package nl.giejay.android.tv.immich.shared.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class NixplayPreferencesTest {
    @Test fun `legacy inactivity preference keeps its key and minutes`() {
        assertEquals("wake_lock_minutes", WAKE_LOCK_MINUTES.key())
        assertEquals(15, WAKE_LOCK_MINUTES.fromPrefValue("15"))
    }

    @Test fun `both legacy and current always on values disable the timeout`() {
        assertEquals(0, WAKE_LOCK_MINUTES.fromPrefValue("always on"))
        assertEquals(0, WAKE_LOCK_MINUTES.fromPrefValue("0"))
    }
}
