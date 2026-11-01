package nl.giejay.android.tv.immich.shared.prefs

import nl.giejay.android.tv.immich.R

/** Retains the preference key and values used by the original Immix builds. */
data object WAKE_LOCK_MINUTES : IntListPref(15, "Motion inactivity timeout",
    "Minutes without motion before allowing the display to sleep; Always on disables the timeout.",
    R.array.wake_lock_minutes, R.array.nixplay_timeout_values) {
    override fun key() = "wake_lock_minutes"
    override fun fromPrefValue(prefValue: String): Int =
        if (prefValue == "always on") 0 else prefValue.toInt()
}
