# WattsOnTV

Android TV app (Kotlin, no AndroidX, no Compose, plain `Activity` + programmatic views) for a Chromecast with Google TV. It connects over BLE to an Assioma Duo pedal (Cycling Power service `0x1818`, characteristic `0x2A63`) and draws the 3 s average watts as an overlay on top of other apps (`TYPE_APPLICATION_OVERLAY`). Not a git repo. User-facing UI text is in Spanish.

## Build and test
- No Android Studio. Gradle needs JDK 17, and the system `java` is not usable: always prefix `JAVA_HOME=/opt/homebrew/opt/openjdk@17`.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest assembleDebug --console=plain`
- Single test: `./gradlew testDebugUnitTest --tests 'app.wattsontv.PowerTest.parsesRealPackets'`
- APK: `app/build/outputs/apk/debug/app-debug.apk`. Only debug builds exist, with no release signing.
- `local.properties` points `sdk.dir` at `/opt/homebrew/share/android-commandlinetools` (Homebrew SDK, platform 34, build-tools 34.0.0). Recreate it if missing.
- Wrapper is Gradle 8.9 with AGP 8.5.2 and Kotlin 1.9.24 (pinned in `build.gradle.kts`). The Homebrew `gradle` (9.x) was only used to generate the wrapper.
- `build/` contains absolute paths and the machine hostname. Delete `build/` and `.gradle/` before sharing the folder.

## Install on the Chromecast (wireless adb)
- `adb` is not on PATH: `export PATH=$PATH:/opt/homebrew/share/android-commandlinetools/platform-tools`
- The Chromecast is a Chromecast HD on Android 14. Wireless debugging has a random port that changes on every toggle or reboot. After a reboot you must run `adb pair IP:PAIRPORT CODE` again, then `adb connect IP:PORT`. Ask the user for the current values. Do not reuse old ones.
- The Chromecast sleeps to a screensaver, so `screencap` returns black. Run `adb shell input keyevent KEYCODE_WAKEUP` first. `screencap` via `exec-out` must write to `/private/tmp/...`, because `/tmp` paths were not found by the Read tool.
- Installs were blocked by Play Protect verification until `adb shell settings put global verifier_verify_adb_installs 0` was set (still disabled on the device).
- After a fresh install, the permissions are granted via adb because the TV settings UI is unreliable:
  `appops set app.wattsontv SYSTEM_ALERT_WINDOW allow`, `pm grant` for `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` and `POST_NOTIFICATIONS`, and `dumpsys deviceidle whitelist +app.wattsontv`.
- Keep `targetSdk = 34`. With 33 Play Protect shows an "unsafe app blocked" dialog on Android 14.

## Code layout (`app/src/main/java/app/wattsontv/`)
- `MainActivity`: the whole UI (two cards: pedal list, appearance with a live preview). It builds everything in code with custom focus states for D-pad use. It scans only while the service is not running.
- `OverlayService`: foreground service (`connectedDevice` type). It owns the overlay `TextView`, the 250 ms refresh tick, the idle auto-stop, and the `PowerBle` instance. Static `@Volatile` fields (`running`, `connected`, `lastWatts`, `status`, `deviceName`) are how the Activity reads state, with no binding.
- `PedalScanner`: scans filtered by service `0x1818` for the pedal list. Only the **left** pedal of a Duo advertises that service. The right one does not, and the app never connects to it.
- `PowerBle`: connects to a given address, subscribes to `0x2A63`, and reconnects (`autoConnect = true`) unless `stop()` was called.
- `PowerParser`: reads watts from bytes 2-3 (sint16 little-endian) of the measurement packet. `PowerAverager`: time-weighted 3 s average, which returns 0 if there is no sample for 3 s.
- `Prefs`: SharedPreferences `settings`. The idle key is `idle_min_v2` on purpose (default 30 min, `0` = never). Do not reuse the old key.

## Behavior decisions to preserve
- Shows only the 3 s average watts: no laps, no left/right balance, no phone or Garmin in the loop. Showing 0 W on no data was explicitly chosen.
- The overlay starts when a pedal is connected from the UI and stops on Disconnect or after the idle timeout. There is no separate overlay start button and no auto-start on boot.
- The Assioma Duo seems to allow a single BLE connection, because the Favero app and this app block each other. That is why disconnect and idle auto-stop exist.
- Do not add the INTERNET permission or third-party dependencies. The user asked about privacy, and the APK was verified to contain no personal data and no network access.

## Naming
- App/project name is `WattsOnTV`. The application ID and package is `app.wattsontv`. The old `app.vatios.overlay` was removed, so ignore stale references.
