# Immix revival: upstream 6.2.0

Branch: `revive/merge-upstream-6.2.0`

Original Immix: `c9dcea6`

Merged upstream: `037cb7ce0d94f36113afd0bec19b84f23ec705ed` (6.2.0, latest upstream main fetched for this update).

## Merge outcome

The histories contain 16 Immix-only and 373 upstream-only commits. The first real merge produced 19 conflicted paths. Conflicts were resolved using current upstream APIs/UI/build files and adapting the fork additions. Upstream now vendors MediaSlider rather than using the old submodule. The original submodule checkout is preserved locally in `.git/immix-original-mediaslider`; it is not part of the resulting source tree.

Retained: Nixplay native motion interface, timeout preference (including old key/value compatibility), remote Power/F1 action, Android Settings shortcut, HOME launcher support, optional `immich.p12` client certificates for API/photos/video, Firebase-free application, and original 3 GiB Glide cache. Current upstream replaces the old hardcoded clock/animation edits with its configurable metadata and playback UI. API-key sign-in is retained; demo/phone-auth options are not offered.

Integration fixes: the reusable slider now has an opt-out for its keep-screen-on flag, enabled only when a Nixplay hardware monitor is active. Missing native library leaves normal playback power handling intact. Android TV Leanback is optional for this non-TV frame. Sensor polling no longer raises the main thread's priority.

No application was installed, pushed to a frame, or published. The package ID remains `nl.giejay.android.tv.immich`; do not install beside an upstream APK with the same ID without considering signatures/data first. HOME alias is registered by an install, but no default HOME change has been made on any device.

## Build

Use a JDK compatible with upstream AGP (JDK 21 recommended), Android SDK platform 37 and the build-tools requested by Gradle. The Gradle wrapper remains upstream's 9.6.1. No global toolchain has been installed.

```sh
./gradlew assembleDebug testDebugUnitTest
```

No Firebase project or google-services.json is required. Default resource values are empty and contain no credentials. Optional extra-string overlays must not redefine those same default names without first removing/replacing the local defaults. Release signing is enabled only when RELEASE_KEYSTORE_PATH is supplied; otherwise release output is unsigned. Workflows build artifacts, do not automatically publish to Play Store or push version commits.

## Validation status

- No merge conflict markers/unmerged paths; XML parsing passes. Whitespace checks against upstream pass; imported upstream history contains pre-existing whitespace warnings.
- Added regression tests for migration of the motion timeout and for leaving TLS untouched when no client certificate exists.
- Android compilation and unit tests have NOT run: this Mac lacks a development JDK and Android SDK; approval to download project-local tools was requested.
- Device behavior is NOT validated.

## Hardware work remaining before deployment

1. Verify the frame's native JNI library loads with this build and determine the minimum privileges required; original Immix instructions used a system app.
2. Test photos, videos, remote inputs and both display orientations on Android 7.1/API 25.
3. Test wake-lock behavior, screen timeout, manual power and sensor wake. The inherited poller still counts polling ticks and uses a timed screen wake lock. This merge does not prove deep-sleep wake or eliminate those inherited limitations. DreamService/preview screen power must also be checked separately.
4. Test Immich 3.3.0 using dedicated credentials, server/network loss and repeated restarts.
5. Before any installation, use the backed-up original launcher and ADB keys, and prepare an explicit rollback. Never begin by disabling both Nixplay packages.

## Repository setup

`origin` points at the original Immix repository; `upstream` points at giejay/Immich-Android-TV. Do not push until a user-owned fork is selected. No remote fork was created.
