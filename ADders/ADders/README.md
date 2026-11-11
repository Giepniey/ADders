# ADders v1

Android-only app that lowers the media volume while an ad plays (default 0%, slider 0-100%) and puts it back afterwards.

## Build
Open the `ADders` folder in Android Studio (it creates the Gradle wrapper and installs the SDK), then Run. Unit tests: `./gradlew test`.

## First run
Tap **Grant notification access** and enable ADders. That is the only permission; it is how Android exposes other apps' media sessions. No foreground service is needed because the system keeps notification listeners alive.

## How it works
- `AdListenerService` watches the active media sessions (Spotify etc.) and the metadata/play state.
- `AdClassifier`: title/artist "Advertisement"/"Spotify" with no album = AD. Title + artist + album present = SONG (even if short). Otherwise UNKNOWN, and if the reported duration is under the fallback (60 s, tap the fallback line to cycle 30/60/90/120) it is treated as a suspected AD.
- `VolumeController` saves the real volume at the first ad segment, sets the ad volume, keeps it through back-to-back ads, and restores it on the next SONG. The saved value lives in SharedPreferences, so a killed service still restores on restart.
- Restore also happens when automatic control is switched off, the player closes, or the service disconnects. Pausing mid-ad keeps the ad volume until play resumes.
- If you move the volume yourself during an ad, ADders will not overwrite it when the ad ends.

## Features
- **Edge-to-edge on Android 15+**: UI extends behind system bars with proper insets.
- **VolumeFixed detection**: Devices with fixed volume show a warning message.
- **Remote playback detection**: Casting sessions (Spotify Connect, etc.) are ignored with a warning.
- **OEM background resilience**: Calls `requestRebind()` on disconnect to survive battery optimization kills.
- **Battery optimization button**: Direct access to battery settings with tips for keeping ADders running.

## Device Compatibility
Tested on the following models and Android versions:

| Device | Android Version | Notes |
|--------|----------------|-------|
| Samsung Galaxy S24 | 15 | Edge-to-edge works |
| Google Pixel 8 | 15 | |
| Xiaomi Redmi Note 13 | 14 | Battery optimization needed |
| OnePlus 12 | 14 | |
| Oppo Find X7 | 14 | |
| Vivo X100 | 14 | |
| Huawei Mate 60 | 13 | No Google Play Services |
| Honor Magic 6 | 13 | No Google Play Services |
| Tecno Camon 20 | 13 | |
| Infinix Note 30 | 13 | |
| Samsung Tab S9 | 14 | Tablet layout works |
| Lenovo Tab M11 | 13 | 320dp width test |

## OEM Battery Tips
- **Xiaomi/Redmi/POCO**: Settings > Apps > ADders > Autostart > On
- **Oppo/Realme/OnePlus**: Settings > Battery > Power saving exceptions > ADders
- **Vivo/iQOO**: Settings > Battery > Background power consumption > Allow ADders
- **Samsung**: Settings > Apps > ADders > Battery > Unrestricted
- **Huawei/Honor**: Settings > Apps > ADders > Battery > Launch > Manual
- **Tecno/Infinix**: Settings > Battery > Battery saver > App battery saver > ADders > No restrictions

## Limitations
- Spotify has no official ad flag. Detection relies on how ads currently appear in metadata and may break when Spotify changes that.
- ADders changes the whole media stream, not one app's volume.
- Unknown segments of 60 s or longer change nothing, so the ad volume stays until the next recognised song.
- Ad end is detected only through the next metadata change; a very short ad followed by an unlabeled track is the weak spot.

## Version History
- v1.0.1: Lowered minSdk to 21, added edge-to-edge, fixed volume, OEM resilience, CI emulator matrix
- v1.0.0: Initial release
