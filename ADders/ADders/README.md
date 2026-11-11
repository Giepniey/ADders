# ADders v1

Android-only app that lowers the media volume while an ad plays (default 0%, slider 0-100%) and puts it back afterwards.

## Build
Open the `ADders` folder in Android Studio (it creates the Gradle wrapper and installs the SDK), then Run. Needs Android 8.0+ (minSdk 26). Unit tests: `./gradlew test`.

## First run
Tap **Grant notification access** and enable ADders. That is the only permission; it is how Android exposes other apps' media sessions. No foreground service is needed because the system keeps notification listeners alive.

## How it works
- `AdListenerService` watches the active media sessions (Spotify etc.) and the metadata/play state.
- `AdClassifier`: title/artist "Advertisement"/"Spotify" with no album = AD. Title + artist + album present = SONG (even if short). Otherwise UNKNOWN, and if the reported duration is under the fallback (60 s, tap the fallback line to cycle 30/60/90/120) it is treated as a suspected AD.
- `VolumeController` saves the real volume at the first ad segment, sets the ad volume, keeps it through back-to-back ads, and restores it on the next SONG. The saved value lives in SharedPreferences, so a killed service still restores on restart.
- Restore also happens when automatic control is switched off, the player closes, or the service disconnects. Pausing mid-ad keeps the ad volume until play resumes.
- If you move the volume yourself during an ad, ADders will not overwrite it when the ad ends.

## Limitations
- Spotify has no official ad flag. Detection relies on how ads currently appear in metadata and may break when Spotify changes that.
- ADders changes the whole media stream, not one app's volume.
- Unknown segments of 60 s or longer change nothing, so the ad volume stays until the next recognised song.
- Ad end is detected only through the next metadata change; a very short ad followed by an unlabeled track is the weak spot.
- Not built or run on a device here (no Android SDK in this environment).
