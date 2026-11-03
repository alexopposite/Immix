# W10F-03 first ordinary-app trial — 2026-11-01

## Applied with user authorization
- Installed the tested debug APK as an ordinary additional app, package nl.giejay.android.tv.immich.
- Provisioned the server URL and private API key using scripts/provision-immich.py --apply. Key contents were not displayed or placed in command arguments.
- Confirmed the app migrated the key to its v1 encrypted-at-rest format on first launch.
- Disabled only Immix's .NixplayLauncher alias with pm disable. The initial pm disable-user command reported default, so it was replaced with the verified pm disable command.
- Android stopped the app when its component state changed; this was not a crash. It was relaunched after setup completed.
- Nixplay remains installed, enabled, and the resolved default HOME app. No system-app installation, system-partition write, Nixplay package disabling, sleep-timeout change, or firmware operation was performed.

## Verified
- Authenticated wireless ADB remains reachable.
- Server album-list request returned HTTP 200 with one accessible album containing six assets.
- On-device album list and covers loaded over HTTPS with certificate verification enabled.
- Album grid displayed photo/video previews, including HEIC photo previews.
- Full-screen photo rendering and metadata display worked through Immich-generated previews.
- Starting slideshow with the Play key advanced from the first slide to the fourth. Portrait merging means slide count differs from asset count.

## Not yet working or validated
- Motion library load is blocked for the ordinary app by Android's classloader namespace: /system/lib/libgpio_jni.so is not accessible. The fallback kept the app running, but Immix motion sleep/wake is not active. System-app promotion or another isolated hardware integration requires separate review and authorization.
- Some original-image requests produced decoder failures; source images include HEIC. Generated previews displayed successfully.
- The video item showed a black picture during the sampled playback, despite the player progress indicator moving. The decoder log reports NoSupport for Dolby Vision/HEVC profile dvhe.08.04 on OMX.google.hevc.decoder, consistent with this frame lacking support for that video profile. Transcoded video playback, audio and repeated loops remain unvalidated.
- Physical remote operation, physical orientation changes, motion sleep/wake, schedules and reboot/autostart are not validated by this trial.

## Rollback
With authenticated wireless ADB connected:

```sh
adb -s 192.0.2.10:5555 shell am force-stop nl.giejay.android.tv.immich
adb -s 192.0.2.10:5555 shell am start -n com.kitesystems.nix.frame/.LaunchActivity
```

Optional complete removal of the ordinary trial app and its private settings/cache:

```sh
adb -s 192.0.2.10:5555 uninstall nl.giejay.android.tv.immich
```

Removal does not delete server photos or original Nixplay data. The default HOME app remains Nixplay. No rollback commands above were executed during the initial trial.

The pre-provisioning private preference snapshot is under ignored .tooling/provisioning-backups. The API-key file remains ignored and owner-only. Keep screenshots and private snapshots out of Git and public issue reports.
