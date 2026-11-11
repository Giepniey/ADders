# ADdersPlayer - iOS Player

This is an iOS-only audio player that demonstrates the ADders fade functionality for your own audio files.

## Hard Limitations

iOS does not allow apps to:
- See what other apps (Spotify, Apple Music, etc.) are playing
- Change system volume

Therefore **ad muting is impossible on iOS**. This app provides a local player with the same fade-in/out settings as Android.

## Features

- Pick audio files from Files app
- Queue management with play/pause/next/previous
- In-app volume slider (0-1, saved in UserDefaults)
- Fade-in and fade-out settings (OFF + 3-15s)
- Audio session management (.playback category, background audio)
- Lock screen controls via MPRemoteCommandCenter
- Now playing info via MPNowPlayingInfoCenter
- Handles interruptions (calls, Siri, alarms)
- Handles route changes (headphones unplugged → pause)

## Deployment Target

iOS 15.0 (iPhone 6s and newer, iPad with iOS 15)

## Dependencies

None - uses only native Swift + SwiftUI + AVFoundation + MediaPlayer

## Project Structure

```
ADdersPlayer/
├── ADdersPlayer/
│   ├── Models.swift          # Track, FadeSchedule, clampFadeDuration
│   ├── AudioPlayer.swift     # AVAudioPlayer wrapper with fade logic
│   └── ContentView.swift     # SwiftUI UI with file picker
└── ADdersPlayerTests/
    └── TestModels.swift      # Unit tests for clamp and fade schedule
```

## Running Tests

```bash
swift test
# or
xcodebuild test -scheme ADdersPlayer -destination "platform=iOS Simulator,name=iPhone 15"
```

## Running on Device

1. Open ADdersPlayer in Xcode
2. Connect iPhone/iPad
3. Select your Apple ID in signing settings
4. Build and Run (Cmd+R)

Requires Mac with Xcode 15+ and iOS 15+ device.

## Settings Key

All settings saved in UserDefaults:
- `selectedVolume`: Float 0-1 (default 1.0)
- `fadeInSec`: Int 0-15 (default 0)
- `fadeOutSec`: Int 0-15 (default 0)

## Limitations

- No hardware volume control (iOS restriction)
- No ad detection (iOS restriction)
- Only plays local files from Files app
- Cannot detect playback from other apps
