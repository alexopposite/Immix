/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.giejay.android.tv.immich

import android.app.Application
import android.content.Context
import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import nl.giejay.android.tv.immich.power.PlaybackPowerPolicy
import nl.giejay.android.tv.immich.shared.prefs.WAKE_LOCK_MINUTES
import nl.giejay.android.tv.immich.shared.prefs.legacyMotionTimeoutMinutes
import org.immix.frame.FrameContract
import org.immix.frame.FrameHardwareClient
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import nl.giejay.android.tv.immich.homescreenchannels.HomeScreenChannelManager
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.USER_ID
import timber.log.Timber
import java.util.UUID


/**
 * Initializes libraries, such as Timber, and sets up application wide settings.
 */
class ImmichApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // AppContextInitializer (initOrder set higher than Firebase's) sets this first;
        // kept as a cheap no-op for direct Application construction.
        appContext = this
        PreferenceManager.init(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        var userId = PreferenceManager.get(USER_ID)
        if(userId.isBlank()){
            userId = UUID.randomUUID().toString()
            PreferenceManager.save(USER_ID, userId)
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) { playbackPower.release(activity) }
        })
        val legacyTimeout = legacyMotionTimeoutMinutes(PreferenceManager.sharedPreference.all[WAKE_LOCK_MINUTES.key()])
        hardwareClient = FrameHardwareClient(this, { state ->
            frameHardwareManaged = state.getBoolean(FrameContract.MANAGED, false)
            playbackPower.setManaged(frameHardwareManaged)
        }, legacyTimeout)
        hardwareClient?.connect()

        HomeScreenChannelManager.initSubscriptions()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                HomeScreenChannelManager.scheduleRefresh("app_foreground")
            }
        })
    }

    companion object {
        var appContext: Context? = null
        var hardwareClient: FrameHardwareClient? = null
        @Volatile var frameHardwareManaged = false
        private val playbackPower = PlaybackPowerPolicy<Activity> { activity, keepOn ->
            if (keepOn) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        fun requestPlaybackScreenOn(activity: Activity, requested: Boolean) {
            playbackPower.request(activity, requested)
        }
    }

}
