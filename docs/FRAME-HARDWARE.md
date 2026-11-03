# Frame hardware helper, prototype v2

This branch moves vendor hardware access out of Immix into a separate ordinary Android app. The current Nixplay backend is a **temporary, manually started root process**, not a permanent installation. Do not follow the inherited README system-app instructions for this design.

## Boundaries

```text
Immix / future display apps
    | signature-protected versioned AIDL, callbacks, activity and bounded leases
frame-contract library
    |
Frame Hardware app (owner settings, policy, foreground service, separate UID)
    | fixed commands over an abstract Unix socket; kernel peer-UID check
Nixplay root adapter (vendor JNI, CPU monitoring lock, display wake/sleep)
    |
Android power service + W10F-03 motion hardware
```

The helper has no Internet permission, API key, Immich connection or image database. Immix stays an ordinary app and no longer loads vendor JNI. The backend accepts only the helper UID or root, not the display-app UID. It executes no arbitrary shell commands and opens no TCP port. Future hardware adapters can implement the same contract without teaching each display app vendor APIs.

On a normal Android phone, motion hardware should use SensorManager and rotation should follow Android configuration/display APIs. An ordinary app cannot assume global sleep/wake privileges; unavailable capabilities must remain unavailable. This rooted Android 7.1 frame is a specific privileged adapter, not a general phone permission workaround. A foreground service is a lifecycle mechanism, not a CPU wake lock. Our polling adapter needs a bounded partial CPU lock during management because a hardware wake interrupt has not been established.

References: [bound services](https://developer.android.com/develop/background-work/services/bound-services), [signature permissions](https://developer.android.com/guide/topics/permissions/defining), [wake-lock guidance](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock).

## Contract v2

`frame-contract` contains AIDL and a Java client. Version 2 requires updating both client and helper APKs together; a mismatched client falls back to ordinary playback. Restart the temporary adapter from the new helper APK too, because v2 adds bounded display-hold commands. Bind explicitly to `org.immix.frame.hardware/.FrameService`; request `org.immix.frame.permission.BIND_HARDWARE`. The permission is signature-level: current clients and provider must share a signing certificate. Adding independently signed third-party apps will require a separately designed owner authorization mechanism, not weakening this permission.

- `getState()` and `registerListener()` provide a snapshot plus changes. Bundle keys include `protocol_version`, `provider_instance`, `state_sequence`, `presence_supported`, `power_control_supported`, `power_policy_active`, `display_on`, `display_rotation`, `presence`, `last_motion_elapsed_ms`, `reason`, `mode`, `power_button_supported`, `timeout_ms` and `timeout_source`.
- The client registers first, then obtains a snapshot, rejects unsupported versions, filters stale sequences and callbacks from a previous provider instance, and reconnects after service death. An unavailable helper returns app power handling to its normal fallback immediately. Playback requests made while the helper owns power are remembered; stopped/destroyed playback is never resurrected.
- `presence` is `unknown`, `recent_motion`, or `no_recent_motion`. No recent motion does **not** prove the room is empty. Timestamps are elapsed time since boot, not UTC. Rotation uses Android Display rotation values 0–3.
- `reportUserActivity()` reports actual remote interaction. It does not bypass an owner-off decision.
- `acquireAwakeLease(token, duration)` defers inactivity only, for at most one hour; leases are limited to eight per UID and 32 total, caller-owned, expire, and are removed when the Binder owner dies. `releaseAwakeLease()` releases one. The convenience client currently exposes observation and user activity; other clients may use the AIDL lease API.
- `requestPowerToggle()` accepts a remote power action only while management is available and owner stay-off/quiet hours permit it. Client key repeats are consumed without a second toggle. Remote sleep yields to a fresh motion signal or ordinary user activity; a held signal must clear first. Owner stay-off remains stronger.
- `offerLegacyTimeoutMinutes()` is a narrow one-time migration exception: only the installed legacy Immix UID with the signature permission can offer it, and only when the helper has no stored timeout. Absent/invalid legacy settings are not offered; existing helper settings always win. Importing never enables management. Ongoing persistent policy configuration remains restricted to helper owner controls.

Immix has a settings shortcut to the helper. While management is active, it suppresses its slider keep-screen-on requests and clears the visible activity flag. Android remains responsible for playback/activity lifecycle and rotation. Physical orientation changes and DreamService behavior still need device testing.

## Owner policy

The helper starts in observe-only, including after a crash or service restart. Owner settings provide inactivity timeout (default 15 minutes; 0 means always on), optional quiet hours, and a manual stay-off override. Priority is manual off, quiet hours, then recent activity / awake lease / always-on, then inactivity sleep. Quiet hours can cross midnight. Motion resets the elapsed-time inactivity timer.

When explicitly enabled, the helper can sleep or wake the **device display regardless of which app is in front**. Apps need the contract to cooperate with playback and request exceptions, but global wake does not depend on their participation. The helper does not launch another activity just to wake the screen.

Immix now forwards POWER/F1 actions it receives to the helper, separately from the stronger owner stay-off override. Android can intercept POWER before an app sees it, and other foreground apps must opt into the contract to forward their own keys. This is not global input interception; the original Immix handled these keys only inside its own activity too. An external power-button sleep may still be reversed by an awake policy. Physical routing and quiet hours need a frame trial before ongoing management. Stock Nixplay still runs and has its own wake locks/power policy; arbitration with it needs a longer test before any approved launcher change.

## Temporary adapter and safety

The root adapter is launched from an APK in `/data/local/tmp` using the existing authenticated root ADB session. It expires after ten minutes. It records the original sensor-power value and restores it on normal exit. During management, a partial CPU wake lock is renewed with a 30-second bound. While policy requests awake, a separate bright-display lock is renewed with the same bound, so Android screen timeout cannot truncate the intended activity interval. The display hold is released before sleep and both holds are released on observe/cleanup/watchdog recovery. If helper heartbeats stop for ten seconds, the adapter wakes a screen it put to sleep and releases its CPU lock. Stopping or returning to observe-only also wakes a screen it put to sleep. A hard kill/power failure cannot run cleanup; no claim of recovery from arbitrary kernel/firmware failure is made.

There is no boot receiver, boot script, `/system` change, system-app promotion, default-launcher change or production persistence in this implementation. APK installation itself persists until uninstall; policy management is opt-in and does not survive a service restart. The backend reports `deep_sleep_wake_supported=false` and `cpu_monitor_required=true`.

## Build and trial commands

From the checkout, using the already approved local tools:

```sh
scripts/gradle-local assembleDebug testDebugUnitTest assembleRelease
adb -s 192.0.2.10:5555 install -r frame-hardware/build/outputs/apk/debug/frame-hardware-debug.apk
adb -s 192.0.2.10:5555 install -r app/build/outputs/apk/debug/ImmichTV-6.2.0.apk
python3 scripts/frame-hardware.py --probe
python3 scripts/frame-hardware.py --start
python3 scripts/frame-hardware.py --status
adb -s 192.0.2.10:5555 shell am start -n org.immix.frame.hardware/.SettingsActivity
```

`--probe` only reads hardware state; it refuses to replace the APK of a running adapter. `--start` enables temporary sensor observation; screen management requires the owner button. Expected status: `presence_supported=true`, `power_control_supported=true`, `mode=observe_only`, `power_policy_active=false`. Installing an APK does not provision or change the Immich key.

Stop the temporary trial:

```sh
python3 scripts/frame-hardware.py --stop
```

This stops the backend and started service; a bound service can remain alive while Immix is connected, but will report `hardware_backend_unavailable` and inactive management. Removing only the helper APK with `adb uninstall org.immix.frame.hardware` removes its settings and returns Immix to ordinary playback fallback. Keep the existing working APK before changing Immix itself; downgrading packages can have separate data/signature constraints. Do not uninstall Immix just to remove this helper.

## Historical v1 validation on W10F-03, 2026-11-01

- Debug and release builds, including release vital lint, pass. 150 unit tests pass: 136 application, four callback ordering, ten policy tests. These cover priority, elapsed inactivity, midnight quiet hours and lease expiry, not all Android IPC behavior.
- Ordinary helper APK runs on Android 7.1.2/API 25 with target SDK 37. Immix binds under its own UID; service diagnostics show one registered callback.
- The temporary root process loads `/system/lib/libgpio_jni.so` using the exact vendor JNI class. Observation captured a positive motion reading during the earlier trial; a controlled walk-by test is still required.
- Kernel peer-UID test: helper UID 10026 accepted; Immix UID 10025 rejected from the privileged socket. Immix has access only through the signature-protected service.
- Helper remains a started foreground service independently of Immix. The service returns to observe-only after restart.
- Backend wake changed PowerManager to Awake. A brief sleep request succeeded with Immix stopped/Nixplay foreground; after heartbeat loss the watchdog restored Awake about ten seconds after the last heartbeat.
- Using owner controls, enabled management plus Manual stay off yielded `power_policy_active=true`, `reason=manual_off`, `display_on=false`, and Display Power OFF. Restarting to observe-only and waking restored the display. No persistent system setting was changed.
- Fixed initial input focus after the frame keyboard opened a permission activity and vendor firmware stopped the foreground helper. Owner controls then worked without that detour. Editing text/keyboard permissions still needs its own trial.

- Cleanup verified: adapter stopped, sensor power restored to its original `false`, and display remained interactive. The installed helper is inactive/observe-only; Immix remains in front and Nixplay remains the default HOME.

Remaining: controlled presence wake after inactivity; physical rotation; long-run sleep/CPU and stock-app interference; physical power-button behavior; lease death and quiet-hours runtime tests; service-loss playback fallback during an active slideshow; and modern-phone runtime testing. Durable privileged startup needs a separate reviewed recovery/persistence design and explicit approval. This prototype does not yet claim unattended production operation.

## Source parity follow-up

See [hardware parity audit](HARDWARE-PARITY.md) for original class mappings, restored behaviors, deliberate fixes and test coverage. The v2 source passes debug/release builds and 183 Android unit tests (145 app, four contract, 34 helper). Five synthetic provisioning tests also pass. These are local JVM/build checks; v2 has **not** been installed or physically tested. Nixplay remains in use. The earlier v1 device results above do not establish v2 runtime parity.
