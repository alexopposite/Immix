package nl.giejay.android.tv.immich.power

import org.junit.Assert.*
import org.junit.Test

class PlaybackPowerPolicyTest {
    private val flags = mutableMapOf<Any, Boolean>()
    private val policy = PlaybackPowerPolicy<Any> { target, value -> flags[target] = value }
    private val screen = Any()
    @Test fun `helper loss immediately restores active playback request`() {
        policy.request(screen, true)
        assertEquals(true, flags[screen])
        policy.setManaged(true)
        assertEquals(false, flags[screen])
        policy.setManaged(false)
        assertEquals(true, flags[screen])
    }
    @Test fun `playback beginning while managed is remembered for fallback`() {
        policy.setManaged(true)
        policy.request(screen, true)
        assertEquals(false, flags[screen])
        policy.setManaged(false)
        assertEquals(true, flags[screen])
    }
    @Test fun `stopped playback is not restored after helper loss`() {
        policy.request(screen, true)
        policy.setManaged(true)
        policy.request(screen, false)
        policy.setManaged(false)
        assertEquals(false, flags[screen])
    }
    @Test fun `idle screens acquire no screen lock when ownership changes`() {
        policy.setManaged(true)
        policy.setManaged(false)
        assertTrue(flags.isEmpty())
    }
    @Test fun `destroyed window is released without affecting another player`() {
        val other = Any()
        policy.request(screen, true); policy.request(other, true)
        policy.release(screen)
        policy.setManaged(true); policy.setManaged(false)
        assertEquals(false, flags[screen])
        assertEquals(true, flags[other])
    }
}
