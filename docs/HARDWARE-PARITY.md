# Immix hardware parity audit

This compares the original Immix hardware implementation at `main` / `baseline/immix-original` (`c9dcea6`) with the separate-helper refactor. The sensor classes originated in Simon Merschjohann's Immix fork, rather than Android TV upstream. They supplied useful separation; their removal from the display app must preserve that separation in the helper.

## Responsibilities and behavior

| Original responsibility | Refactor implementation | Verification |
| --- | --- | --- |
| `HardwareSensor.isActivityDetected()` abstraction | `PresenceSensor`, implemented by `SocketHardwareAdapter` for the current backend; controller accepts any implementation | Fake sensor independent of fake display adapter |
| `ActivitySensor` coordinates sensing and power | Pure `FrameController` plus Android `FrameService` | Motion, inactivity, recovery and exception tests |
| `SensorPoller` default 15-minute inactivity timeout | `FramePolicy`; elapsed time since motion/user activity | Existing and new timeout boundary tests |
| No timeout / Always on | Zero timeout; renew display hold while managed | Always-on test beyond old ten-minute lock duration |
| Legacy `wake_lock_minutes` numeric strings / `always on` | Legacy decoder plus connection-time offer to protocol-v2 helper | Decoder and migration acceptance/refusal tests |
| Changes to timeout take effect at runtime | Owner helper controls update worker policy immediately | Source path; Android UI trial still required |
| `PowerUtils` keeps display bright during activity | `DisplayPower` abstraction; root backend renews bounded bright-display and CPU holds | Controller hold renewal/release tests; new native hold path requires device testing |
| `SensorServiceCallback.sleep/wakeUp` boundary | Pure policy decision passed through `DisplayPower` | No duplicate sleep/wake for unchanged interactive state |
| Motion wakes after inactivity | Fresh sensor observation resets activity and requests display awake | Controller sleep -> motion -> wake test |
| POWER/F1 sleep/wake from Immix activity | MainActivity and screensaver key callbacks forward to capability-gated helper request | Policy toggle tests; physical key routing requires device testing |
| Remote wake resets inactivity | `FramePolicy.togglePower` / ordinary activity clear remote sleep and reset elapsed timer | Wake interval boundary and second-press tests |
| Non-Nixplay fallback | Helper unavailability leaves ordinary app playback screen requests active; JNI never loads in Immix | Immediate fallback and no-idle-lock tests |
| Native class/method linkage | Exact vendor class `com.kitesystems.nix.frame.MotionSensor` remains in helper | Earlier v1 probe; v2 build; no new frame installation |

## Migration ownership

Immix offers only an actually stored, valid legacy value. Zero / `always on` is preserved. The helper accepts the offer only from the authorized installed Immix UID and only if no `timeout_ms` exists. A previous import or helper-owner choice always wins. Owner configuration and import execute on the same worker, so a late queued import cannot overwrite an earlier owner save. Importing never enables management, clears quiet hours, or removes an owner override.

After migration, the helper owns future settings. Immix's retired hardware preference remains readable for migration; it is not a second live source of policy changes. A helper configured during the earlier prototype trial already has a timeout and deliberately will not import over it. Its owner can change the timeout through helper settings.

## Intentional corrections rather than bug-for-bug behavior

- The old poller added 1,000 milliseconds per call; the replacement measures actual elapsed inactivity even when polling is delayed.
- The old bright wake lock expired after ten minutes and was not renewed while held. Bounded renewal now covers the full configured interval, including Always on.
- The old sleep operation only released its own wake lock. The privileged adapter explicitly requests display sleep; this still needs coexistence testing with stock Nixplay power policy.
- A remote sleep action is separate from owner stay-off. Fresh motion or regular remote interaction can clear remote sleep, but cannot override owner stay-off or quiet hours. A sensor level already high at the time of remote sleep must clear before a new high can wake it, preventing an immediate unwanted wake.
- Repeated key-down events do not repeatedly toggle the display. Unavailable/observe-only/incompatible helpers do not consume the key as a successful hardware action.
- Losing helper ownership immediately reapplies only active playback requests, including playback started while managed. It does not keep idle activities awake or restore requests released by stopped/destroyed players.
- Sensor/power failures relinquish management and report unavailable capabilities. The controller does not pretend a failing sensor means the room is empty.

## Validation and remaining work

Local checks: debug and release builds (including release vital lint) pass; 183 Android unit tests pass with zero failures/errors: 145 app, four contract, 34 helper. Five synthetic provisioning tests also pass. New behavior tests cover elapsed timing, motion wake, remote toggling, held-motion suppression, owner/quiet-hour precedence, display hold renewal, denied hardware operations, backend recovery, one-time migration and playback fallback.

The final full-suite run also exposed an existing timeline test race: fetch callbacks completed before the cache StateFlow published. Its test now awaits the published cache instead of callback completion; production timeline code is unchanged.

These tests exercise actual pure controller/policy/request-tracking code with independent sensor and power fakes. They do not prove Android Binder scheduling, native wake-lock calls, or physical remote routing. No emulator is installed and no device command was run in this follow-up. The frame remains on Nixplay.

The source gaps identified in the audit are addressed; runtime parity is still pending an approved trial of **both v2 APKs and the rebuilt temporary adapter**. Verify existing-timeout migration on a fresh helper profile without erasing the user's real app data, physical POWER/F1 behavior, fresh-motion wake, the display staying bright for the configured interval, helper-loss fallback during playback, quiet hours and rotation. Permanent startup/global key interception are separate work, not implied by parity with the original activity-scoped implementation. Management continues to reset to observe-only after helper restart; the adapter remains a ten-minute manual trial.
