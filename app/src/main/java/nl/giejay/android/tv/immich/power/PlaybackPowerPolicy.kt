package nl.giejay.android.tv.immich.power

import java.util.WeakHashMap

/** Main-thread screen requests: remember playback intent even while a helper owns power. */
class PlaybackPowerPolicy<T : Any>(private val apply: (T, Boolean) -> Unit) {
    private val requests = WeakHashMap<T, Boolean>()
    private var managed = false
    fun request(target: T, requested: Boolean) {
        if (requested) requests[target] = true else requests.remove(target)
        apply(target, requested && !managed)
    }
    fun setManaged(value: Boolean) {
        if (managed == value) return
        managed = value
        requests.keys.toList().forEach { apply(it, !managed) }
    }
    fun release(target: T) { requests.remove(target); apply(target, false) }
}
