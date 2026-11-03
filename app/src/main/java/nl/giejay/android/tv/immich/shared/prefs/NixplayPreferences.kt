package nl.giejay.android.tv.immich.shared.prefs

import nl.giejay.android.tv.immich.R

/** Retains the preference key and values used by the original Immix builds. */
data object WAKE_LOCK_MINUTES : IntListPref(15, "Motion inactivity timeout",
    "Minutes without motion before allowing the display to sleep; Always on disables the timeout.",
    R.array.wake_lock_minutes, R.array.nixplay_timeout_values) {
    override fun key() = "wake_lock_minutes"
    override fun getValue(sharedPreferences: android.content.SharedPreferences): Int =
        legacyMotionTimeoutMinutes(sharedPreferences.all[key()]) ?: defaultValue
    override fun fromPrefValue(prefValue: String): Int =
        legacyMotionTimeoutMinutes(prefValue) ?: 15
}

/** Null distinguishes an absent/invalid legacy setting from an explicit Always on selection. */
fun legacyMotionTimeoutMinutes(raw: Any?): Int? {
    val minutes = when (raw) {
        is Int -> raw
        is String -> if (raw == "always on") 0 else raw.toIntOrNull()
        else -> null
    }
    return minutes?.takeIf { it in 0..1440 }
}

/** Normalize old typed storage before generic String preference observers subscribe. */
fun normalizeLegacyMotionTimeoutStorage(preferences: android.content.SharedPreferences) {
    val raw = preferences.all[WAKE_LOCK_MINUTES.key()]
    if (raw == null || raw is String) return
    val minutes = legacyMotionTimeoutMinutes(raw)
    val edit = preferences.edit()
    if (minutes == null) edit.remove(WAKE_LOCK_MINUTES.key())
    else edit.putString(WAKE_LOCK_MINUTES.key(), minutes.toString())
    edit.apply()
}
