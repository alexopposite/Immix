package nl.giejay.android.tv.immich.shared.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import android.content.SharedPreferences
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NixplayPreferencesTest {
    @Test fun `legacy inactivity preference keeps its key and minutes`() {
        assertEquals("wake_lock_minutes", WAKE_LOCK_MINUTES.key())
        assertEquals(15, WAKE_LOCK_MINUTES.fromPrefValue("15"))
    }

    @Test fun `both legacy and current always on values disable the timeout`() {
        assertEquals(0, WAKE_LOCK_MINUTES.fromPrefValue("always on"))
        assertEquals(0, WAKE_LOCK_MINUTES.fromPrefValue("0"))
    }
    @Test fun `absent or invalid legacy values do not seed the helper`() {
        listOf(null, "bad", "-1", "1441", true, Long.MAX_VALUE).forEach {
            assertNull(legacyMotionTimeoutMinutes(it))
        }
    }
    @Test fun `legacy typed and string values keep minutes and always on`() {
        assertEquals(45, legacyMotionTimeoutMinutes("45"))
        assertEquals(45, legacyMotionTimeoutMinutes(45))
        assertEquals(0, legacyMotionTimeoutMinutes("always on"))
    }
    @Test fun `direct preference read also accepts old always on storage`() {
        val preferences = mock<SharedPreferences>()
        whenever(preferences.all).thenReturn(mapOf("wake_lock_minutes" to "always on"))
        assertEquals(0, WAKE_LOCK_MINUTES.getValue(preferences))
    }
    @Test fun `direct preference read falls back safely for malformed storage`() {
        val preferences = mock<SharedPreferences>()
        whenever(preferences.all).thenReturn(mapOf("wake_lock_minutes" to "invalid"))
        assertEquals(15, WAKE_LOCK_MINUTES.getValue(preferences))
    }
}
