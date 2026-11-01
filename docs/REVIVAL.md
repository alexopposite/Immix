# Immix revival: upstream 6.2.0

> Historical merge snapshot. Subsequent ordinary-app trials are recorded in [DEVICE-TRIAL.md](DEVICE-TRIAL.md). The separate [hardware helper](FRAME-HARDWARE.md) now supersedes the retained in-app native poller and remote-power behavior described below. Current combined builds have 150 passing unit tests. No permanent helper startup has been installed.

Branch: `revive/merge-upstream-6.2.0`

Original Immix: `c9dcea6`

Merged upstream: `037cb7ce0d94f36113afd0bec19b84f23ec705ed` (6.2.0, latest upstream main fetched for this update).

## Merge outcome

The histories contain 16 Immix-only and 373 upstream-only commits. The first real merge produced 19 conflicted paths. Conflicts were resolved using current upstream APIs/UI/build files and adapting the fork additions. Upstream now vendors MediaSlider rather than using the old submodule. The original submodule checkout is preserved locally in `.git/immix-original-mediaslider`; it is not part of the resulting source tree.

Retained: Nixplay native motion interface, timeout preference (including old key/value compatibility), remote Power/F1 action, Android Settings shortcut, HOME launcher support, optional `immich.p12` client certificates for API/photos/video, Firebase-free application, and original 3 GiB Glide cache. Current upstream replaces the old hardcoded clock/animation edits with its configurable metadata and playback UI. API-key sign-in is retained; demo/phone-auth options are not offered.

Integration fixes: the reusable slider now has an opt-out for its keep-screen-on flag, enabled only when a Nixplay hardware monitor is active. Missing native library leaves normal playback power handling intact. Android TV Leanback is optional for this non-TV frame. Sensor polling no longer raises the main thread's priority.

No application was installed, pushed to a frame, or published. The package ID remains `nl.giejay.android.tv.immich`; do not install beside an upstream APK with the same ID without considering signatures/data first. HOME alias is registered by an install, but no default HOME change has been made on any device.

## Build

The approved project-local toolchain is installed under `.tooling/`: Temurin JDK 21.0.13+9, Android SDK platform `platforms;android-37.0`, build-tools 36.0.0 (requested by AGP), additional build-tools 37.0.0, and platform-tools 37.0.1. The Gradle wrapper remains upstream's 9.6.1. Gradle caches are in `.gradle-local/`. No global JDK or SDK was installed. Download checksums are recorded in `.tooling/toolchain-manifest.json`.

Use `scripts/gradle-local` to select these tools and keep Android/Gradle state in this checkout:

```sh
scripts/gradle-local assembleDebug testDebugUnitTest
scripts/gradle-local assembleRelease
```

No Firebase project or google-services.json is required. Default resource values are empty and contain no credentials. Optional extra-string overlays must not redefine those same default names without first removing/replacing the local defaults. Release signing is enabled only when RELEASE_KEYSTORE_PATH is supplied; otherwise release output is unsigned. Workflows build artifacts, do not automatically publish to Play Store or push version commits.

## Validation status

- No merge conflict markers/unmerged paths; XML parsing passes. Whitespace checks against upstream pass; imported upstream history contains pre-existing whitespace warnings.
- Added regression tests for migration of the motion timeout and for leaving TLS untouched when no client certificate exists.
- Debug APK built successfully; 136 unit tests in 29 suites passed, with zero failures/errors/skips.
- Minified unsigned release APK built successfully, including release vital lint checks.
- Release R8 mapping/seed checks confirm the native MotionSensor class, all four native methods, and WAKE_LOCK_MINUTES preference object are retained. An explicit JNI keep rule prevents dropping unused methods that the vendor library may register.
- The first compile caught the upstream slider accepting only DefaultHttpDataSource.Factory. Its public setters now accept DataSource.Factory, supporting both default HTTP and the fork's certificate-aware OkHttp playback.
- APK manifest: minSdk 24 (Android 7.0), targetSdk 37. This includes the frame's Android 7.1.2, but does not establish runtime compatibility.
- Build artifacts: `app/build/outputs/apk/debug/ImmichTV-6.2.0.apk` (development signing) and `app/build/outputs/apk/release/ImmichTV-6.2.0.apk` (unsigned). Neither was installed.
- Device behavior is NOT validated.

## Hardware work remaining before deployment

1. Verify the frame's native JNI library loads with this build and determine the minimum privileges required; original Immix instructions used a system app.
2. Test photos, videos, remote inputs and both display orientations on Android 7.1/API 25.
3. Test wake-lock behavior, screen timeout, manual power and sensor wake. The inherited poller still counts polling ticks and uses a timed screen wake lock. This merge does not prove deep-sleep wake or eliminate those inherited limitations. DreamService/preview screen power must also be checked separately.
4. Test Immich 3.3.0 using dedicated credentials, server/network loss and repeated restarts.
5. Before any installation, use the backed-up original launcher and ADB keys, and prepare an explicit rollback. Never begin by disabling both Nixplay packages.

## Repository setup

`origin` points at the original Immix repository; `upstream` points at giejay/Immich-Android-TV. Do not push until a user-owned fork is selected. No remote fork was created.
