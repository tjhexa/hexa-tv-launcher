# Home — custom Android TV launcher

A minimal, hand-built launcher for the Xiaomi **MiTV-MOOQ1** (`tarzan`,
Android TV 10 / API 29). App icons run around the screen edge, the centre is
left free for the clock and wallpaper, and everything is driven by the D-pad.

The app is **sideloaded** and intentionally not the system default home:
Xiaomi's firmware blocks third-party home replacement. Google TV stays the
HOME app; launch **Home** from the Google TV *Apps* list (it has a `HOME`
banner) or via ADB.

## Features

- **Perimeter ring** — apps fill the left column, bottom row and right column.
  The ring rotates under a fixed bottom-centre anchor, so icons flow
  left → bottom → right with Left/Right.
- **Move mode** — hold OK on a tile, then Left/Right to move it; OK finishes,
  MENU hides it. Order is persisted.
- **Favourites** — the ring always ends with an **All apps** tile. In the grid,
  hold OK to pin/unpin (gold ★). The ring shows favourites when any exist.
- **50 baked wallpapers** — hourly rotation by default; interval choices from
  30 minutes to daily or never. Picker with thumbnails (OK set, hold OK
  skip/unskip), *CURRENT* marker, crossfade, brightness-aware scrims.
- **Video wallpaper** — a muted, 9.7 s seamless loop of Jatayu and the Pushpaka
  Vimana, selectable as the last picker tile and included in the rotation. It
  plays on a `SurfaceView` behind the UI, pauses when the launcher is not
  visible, and releases the decoder when a still wallpaper is selected.
- **Clock/date** — `hh:mm`, 12/24-hour, Small/Medium/Large, date on/off.
- **Wordmark** — Cinzel "TJHEXA" top-right with a periodic light sweep;
  on/off and Gold/Ivory/Silver.
- **Quick menu** — press **Up** on the home screen (static SETTINGS gear,
  top-left). Contains Settings (popup), TV settings, Network settings,
  Live TV, App info for the current ring app, and Close.
- **Sorting** — Manual, A–Z, Most used, Recent (launch counts tracked locally).
- **Sounds** — synthesised navigation shimmer and a low launch pluck, played
  with `SoundPool`.
- **Screensaver (`DreamService`)** — wallpaper, large clock, date and wordmark.

## Requirements

- JDK 17+ (built with JDK 21)
- Android SDK: platform 34, build-tools 34+
- Gradle 8.7+ via the included wrapper (AGP 8.5.2).
- ADB access to the TV (this project used `192.168.1.3:5555`).

Create `local.properties` (git-ignored):

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

## Build

```bash
# debug
./gradlew assembleDebug

# release (R8 minify + resource shrink)
./gradlew assembleRelease
```

Release is signed with the local **debug keystore** on purpose, so
`adb install -r` updates the installed app in place and keeps preferences.
A production key would require an uninstall (and lose app data).

Outputs:

```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

## Install / update

```bash
adb connect <tv-ip>:5555
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.hexa.hometv/.HomeActivity

# optional: AOT-compile for faster startup
adb shell cmd package compile -m speed -f com.hexa.hometv
```

To keep it reachable from the TV's app list, the manifest declares
`LEANBACK_LAUNCHER` + `LAUNCHER` and a 320×180 TV banner.

## Controls

| Input | Action |
|---|---|
| Left / Right | Rotate the app ring |
| Down | Next app |
| Up | Open Quick Menu (home) |
| OK | Launch focused app / confirm |
| Hold OK | Enter move mode (home) |
| OK / Back in move mode | Finish moving |
| MENU in move mode | Hide focused app |
| Back | Close popup, quick menu, grid or picker |
| Quick Menu → Settings | Popup settings |

## Resetting / maintenance

- **Unhide all apps** and **Clear favourites** are in Settings.
- **New background now** advances immediately; **Reset skipped wallpapers**
  restores the full rotation pool.
- The stock launcher is never modified or disabled.

## Project layout

```
app/src/main/
  AndroidManifest.xml            HOME + LEANBACK activities, dream service
  java/com/hexa/hometv/
    HomeActivity.kt              composition, clock, settings, quick menu, overlays
    PerimeterView.kt             perimeter ring, focus, move mode
    AppRepo.kt                   launchable-app discovery, sort, icon decode
    AllAppsOverlay.kt            5-column app grid with favourites
    WallpaperPicker.kt           50-thumbnail picker, skip markers
    Wallpapers.kt                asset lookup/decoding
    VideoBackground.kt           muted looping SurfaceView + MediaPlayer
    Prefs.kt                     all persisted state
    ShimmerTextView.kt           Cinzel wordmark + light sweep
    HomeDreamService.kt          screensaver
  assets/walls/wall01..50.jpg    baked 1920×1080 wallpapers
  assets/video/                  Jatayu/Vimana loop + poster frame
  assets/fonts/cinzel.ttf        SIL OFL 1.1
  res/raw/nav_shimmer.wav        navigation sound
  res/raw/launch_pluck.wav       launch sound
  res/drawable-nodpi/banner.png  Android TV banner
```

## GitHub Actions

Public repositories get GitHub-hosted runners for free; the workflow at
`.github/workflows/build.yml` builds a release APK on every push to `main`,
on `v*` tags, and manually via **Actions → Build release APK → Run workflow**.

- Every run uploads an APK artifact (30-day retention).
- Pushing a tag like `v1.0.0` also creates a GitHub Release with the APK
  attached.

### CI signing

By default CI generates a throwaway debug keystore, so the resulting APK is
valid but has a different signature from your local install (uninstall before
installing it). To sign consistently, add these repository secrets:

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | `base64 -w0 release.keystore` |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key password |

When `SIGNING_KEYSTORE_BASE64` is present, the workflow decodes it to a
temporary file and Gradle signs with it; otherwise it falls back to the
generated debug key.

## Regenerating wallpapers

The wallpaper set and TV banner are produced by `../build_wallpapers.py`
(one level above this folder). It downloads Bing's recent daily images and
the owner's Hogwarts image, crops/resizes them to 1920×1080, and writes them
into `app/src/main/assets/walls/`.

## Notes and limitations

- The app has **no network permission**. This TV blocks socket creation for
  its UID, so wallpapers are baked in rather than fetched at runtime.
- No root. The stock Google TV launcher remains installed and enabled.
- Bundled artwork is for personal use: the owner-supplied Hogwarts fan art,
  Bing wallpapers and the Jatayu/Vimana trailer excerpt are not redistributed
  by this project. Cinzel is SIL OFL.
- Measured on the target TV: ~46 MB PSS, 0 % janky frames, ~6 ms median.

## Enabling the screensaver

Settings → Device Preferences → Screen saver → **Home**, or:

```bash
adb shell settings put secure screensaver_components com.hexa.hometv/.HomeDreamService
adb shell settings put secure screensaver_enabled 1
```
