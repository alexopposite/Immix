> **Local revival work:** this branch merges Immich Android TV 6.2.0 into Immix.
> See [revival notes](docs/REVIVAL.md) for changes, build status and hardware limitations.
> The historical installation procedure below is not an approved procedure for W10F-03.
> Ordinary-app trials have now run on W10F-03; see [device results](docs/DEVICE-TRIAL.md).
> The new [hardware helper](docs/FRAME-HARDWARE.md) replaces the inherited in-app sensor implementation. Its root adapter is temporary; automatic startup is not installed.

# Immix

See [personal fork workflow and branch comparisons](docs/FORK-WORKFLOW.md) for the original baseline, upstream revival and separate hardware-helper review.

## Fork Notice
_THIS IS A FORK_ of `Immich Android TV` specifically tailored for use on a Nixplay frame.

The original fork had the following changes (hardware behavior is superseded by the helper linked above):
- Added Android Settings to settings page
- Added motion sensor to turn the display on, set the wakelock in the settings. Default value: 15 minutes.
- Added POWER button on remote to turn of screen (after display turnoff time)
- Added abilitiy to use client certificates (put it in /storage/emulated/0/Android/data/nl.giejay.android.tv.immich/files/immich.p12 with password 'immich')
- Added the app as HOME-launcher app
- Removed all Firebase and Cast functionality
- Removed donation button as it is not working on the frame. Feel free to support the original author: https://github.com/giejay/Immich-Android-TV

## Historical W10E installation (not the current W10F-03 procedure)

Disclaimer: This guide is based on the W10E model. Please note that improper handling or software errors may damage your device. You are the only one responsible for any damages caused by your modification to either hardware or software.

To open the device, you can use a credit card or the IFixit opener, which worked well and left no visible marks after reassembly.

Behind the display, there is a USB port on a circuit board. The display is lightly taped to the metal backing, which can be carefully removed for better access to the controller.

You may need to unscrew the board to connect a USB cable to the port. The frame will automatically power on when connected to a PC.

As soon as you have access to ADB over USB you can start replacing the software.

1. Disable the original software on the frame.
```bash
adb shell
$ su
$ pm disable com.kitesystems.nix.prod & pm disable com.kitesystems.nix.frame
```
2. Enable ADB over network to ensure that you can access the device later on, even without opening the frame again.
```bash
adb shell
$ su
$ setprop persist.adb.tcp.port 6666
```
3. install the app. Note: It must be a system app to access the motion sensor.
```bash
adb root && adb remount && adb push app-debug.apk /system/app/immich.apk && adb reboot
```
4. (optional) Copy client certificate to the frame (p12 password must be 'immich')
```bash
adb push immich.p12 /storage/emulated/0/Android/data/nl.giejay.android.tv.immich/files/immich.p12
```
5. Setup your credentials. You can use [scrcpy](https://github.com/Genymobile/scrcpy) to screen share the Nixplay screen to your PC. You can also send the Immich host information using adb (`adb shell input text https://demo.immich.app`)
6. Go to Android Settings (using settings menu or by `adb shell am start -a android.settings.SETTINGS`). Set the display to turn off after shortest time, disable screensaver if any.
   Or use `adb shell settings put system screen_off_timeout 1` for absolute minimal time.

*Enjoy!*


## original documentation

Immich is a self hosted backup solution for photos and videos. Current features include:

- Upload and view videos and photos
- Auto backup when the app is opened
- Selective album(s) for backup
- Multi-user support
- Album and Shared albums

More info here: https://github.com/immich-app/immich

## Compatibility

This app is compatible with Immich server **v3.0.0 and newer**. Immich 3.0.0 introduced several
breaking API changes (album asset loading via `search/metadata`, search visibility defaults,
error response format); this app has been adapted accordingly. The feature-by-feature API-key permissions for this revived branch are documented below.

This Android TV app will allow you to view those uploaded photos and videos. Current features
include:

| Features                                                                       | Status       |
|:-------------------------------------------------------------------------------|--------------|
| Sign in by phone (https://github.com/giejay/Immich-Android-TV-Authentication)  | Done         |
| Sign in by entering API key                                                    | Done         |
| Demo environment                                                               | Done         |
| Album fetching + Lazy loading                                                  | Done         |
| Showing the photos inside an album                                             | Done         |
| Showing people, random, recent or seasonal photos                              | Done         |
| Slideshow of the photos and videos with a configured interval                  | Done         |
| Setting the app as the screensaver                                             | Done         |
| Setting the albums to show in the screensaver                                  | Done         |
| Configure the interval of the screensaver                                      | Done         |
| Add generic sorting of albums and photos                                       | Done         |
| Add sorting for specific album (select last item in row and press right again) | Done         |
| Showing the 4K thumbnail instead of the full image to speed up loading         | Done         |
| Showing the EXIF data and improving the slideshow view                         | Done         |
| Configure whether to play sound with videos                                    | Done         |
| Smarter merging of portrait photos (same people, same date, same city)         | Todo         |
| Add transitions to slideshow                                                   | Done         |
| Add places/tags view                                                           | Todo         |
| Add background media playing info to screensaver                               | Todo         |
| Casting capabilities                                                           | Out of scope |
| Searching in and for albums                                                    | Todo         |
| Dependency injection with Hilt/Dagger                                          | Todo         |

## Immich connection and API-key permissions

These instructions apply to the revived Immix branch and were checked against the app's
API calls and Immich **3.3.0** server permission declarations. On-device testing is still pending.
The inherited upstream permission list was broader than the endpoints this client uses.

### Create the key

1. Use a normal, non-admin Immich account. A dedicated **Photo Frame** account is recommended;
   share only the desired albums with it as **Viewer**. An existing account also works, but its
   key can access that account's permitted library, not just albums selected in Immix.
2. Sign in to that account in the Immich web interface, open **Account Settings → API Keys**,
   and create a dedicated key named, for example, `Nixplay Immix`.
3. For the complete read-only browsing interface, select these seven permissions:

   ```text
   album.read
   asset.read
   asset.view
   asset.download
   person.read
   memory.read
   folder.read
   ```

   Do not select every permission. No administrative, upload, delete, album-editing,
   or API-key-management permissions are needed to display photos.
4. In Immix, choose **Sign in by API key**, enter the Immich server's base URL
   (for example `https://photos.example.com`, without `/api`) and the generated key.
   Leave **Disable SSL verification** and **Debug mode** unchecked.
   This build connects directly to Immich; no ImmichFrame companion service is needed.
5. Select the desired album(s) in the app. Do not put a real key in source files, screenshots,
   issue reports, or a chat. Revoke the dedicated key in Immich if retiring the frame.

### Provision from a private local file (no remote typing)

The repository-root file `immich_api.key` can hold only the API key as UTF-8 plain text.
Keep it ignored and untracked, and restrict access with `chmod 600 immich_api.key`.
Rich text, Keynote documents, and binary property lists are not accepted as key files.
The key is never embedded in an APK or a Git commit.

From this checkout, validate the private file and prepare a payload without connecting to ADB:

```sh
python3 scripts/provision-immich.py
```

Only after an ordinary-app installation and explicit deployment approval, use
`python3 scripts/provision-immich.py --apply`. The helper requires the existing root ADB
connection, stops Immix, backs up previous settings privately under ignored `.tooling/`,
and writes the URL/key into the app-private preferences over stdin, not command arguments.
It preserves other settings, enables certificate verification and disables debug logging.
It does not install, launch, select a launcher, disable Nixplay, or change screen/wireless settings.
System-app UIDs are deliberately rejected pending separate review.

The app attempts to encrypt provisioned credentials on its next launch using Android Keystore.
Verify that this succeeds on the actual frame: the current app can fall back to plaintext
if the keystore fails. Until that launch, the provisioned key is plaintext in private app storage.
The provisioning helper was tested with synthetic credentials and on this W10F-03. On-device preferences were verified encrypted after launch; see the device trial report.

### Which features need which permissions?

| Permission | Use in this client |
| --- | --- |
| `album.read` | List albums and retrieve the selected album's metadata. |
| `asset.read` | Read asset metadata; search album/random/recent photos; load timeline buckets. |
| `asset.view` | Display previews/thumbnails and stream video playback. |
| `asset.download` | Retrieve original files when original-quality media is requested. |
| `person.read` | People browsing and person thumbnails; optional if those features are not used. |
| `memory.read` | Memories; optional if memories are not used. |
| `folder.read` | Folder/path browsing; optional if folders are not used. |

For an album-only slideshow trial, start with the first four permissions. The other three
are recommended for the full browsing interface, since it may request data for those sections;
with a smaller key, unused sections may display permission errors. This smaller profile has
not yet been tested on the frame.

A key's permissions restrict operations, while the account and album-sharing permissions
restrict which photos it can access. Selecting an album in Immix is a display filter, not
an access-control boundary. A Viewer account may also be unable to change shared assets,
regardless of which API-key scopes are selected.

### Optional write permission: favorites

`asset.update` is required only if you want Immix's favorite/unfavorite control to change an
asset on the server. **Leave it off for a read-only frame.** Slideshow playback does not need it;
favorite changes will fail with the app's error message. This permission permits other asset
updates too, so it is broader than a favorites-only grant.

### Why the older list is not required

This client does not call endpoints requiring `activity.read`, `album.download`, `archive.read`,
`face.read`, `library.read`, `partner.read`, `session.read`, `tag.asset`, `tag.read`, or
`timeline.read`. In Immich 3.3.0, the timeline bucket endpoints used here require `asset.read`,
not `timeline.read`. This is based on the current client endpoint inventory, not a promise that
future client/server versions will never need additional permissions.

References: [client endpoint interface](app/src/main/java/nl/giejay/android/tv/immich/api/service/ApiService.kt),
[favorite action](app/src/main/java/nl/giejay/android/tv/immich/slider/FavoriteService.kt), and the
Immich 3.3.0 [album](https://github.com/immich-app/immich/blob/v3.3.0/server/src/controllers/album.controller.ts),
[search](https://github.com/immich-app/immich/blob/v3.3.0/server/src/controllers/search.controller.ts),
[media](https://github.com/immich-app/immich/blob/v3.3.0/server/src/controllers/asset-media.controller.ts),
[timeline](https://github.com/immich-app/immich/blob/v3.3.0/server/src/controllers/timeline.controller.ts),
[people](https://github.com/immich-app/immich/blob/v3.3.0/server/src/controllers/person.controller.ts),
[memory](https://github.com/immich-app/immich/blob/v3.3.0/server/src/controllers/memory.controller.ts),
and [folder](https://github.com/immich-app/immich/blob/v3.3.0/server/src/controllers/view.controller.ts) controllers.

## Screenshots

|                                                                                    |                                                                      |                                                                                    |
|:----------------------------------------------------------------------------------:|:--------------------------------------------------------------------:|:----------------------------------------------------------------------------------:|
|        ![Alt text](/screenshots/homescreen-1.png?raw=true "Album overview")        |      ![Alt text](/screenshots/photos.png?raw=true "All photos")      |      ![Alt text](/screenshots/sorting-options.png?raw=true "Sorting options")      |
|         ![Alt text](/screenshots/home-edit.png?raw=true "Edit homescreen")         | ![Alt text](/screenshots/settings-view.png?raw=true "View settings") | ![Alt text](/screenshots/settings-screensaver.png?raw=true "Screensaver settings") |
| ![Alt text](/screenshots/screensaver-portrait.png?raw=true "Screensaver portrait") |        ![Alt text](/screenshots/people.png?raw=true "People")        |             ![Alt text](/screenshots/seasonl.png?raw=true "Seasonal")              |

## Build steps

1. Clone project with `git clone --recurse git@github.com:giejay/Immich-Android-TV.git`
2. Create an account at firebase and create a google-services.json file, or
   `cp app/google-services.example app/google-services.json`
3. copy app/strings_other.xml.example to app/src/main/res/values/strings_other.xml and modify
   the address and API keys for your demo server.
4. Build apk with `./gradlew assembleRelease`

## Support the project

You can support the project in several ways. The first one is by creating nice descriptive bug
reports if you find any: https://github.com/giejay/Immich-Android-TV/issues/new/choose.
<br><br>Even better is creating a PR: https://github.com/giejay/Immich-Android-TV/pulls.
<br><br>
Lastly, if you feel this Android TV app is a useful addition to the already great Immich app, you
might consider buying me a coffee or a beer:

[!["Buy Me A Coffee"](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://www.buymeacoffee.com/giejay)

## FAQ

#### I'n not able to set the app as a screensaver

1. Enable development mode on the device (click the build number or "Android TV OS Build" 7 times in
   the System->About settings).
2. Go to System -> Developer Options and enable USB Debugging.
3. If you don't have ADB installed on your PC, follow these
   instructions: https://www.xda-developers.com/install-adb-windows-macos-linux/
4. After downloading/installing ADB on the PC, connect to the device using it's IP: adb connect
   192.168.xx.xx.
5. If you have an Amazon Fire TV, first execute this command and reboot after: 'adb shell settings put secure amazon_ambient_enabled 0'
6. Execute the following command: 'adb shell settings put secure
   screensaver_components nl.giejay.android.tv.immich/.screensaver.ScreenSaverService'
7. Done!
