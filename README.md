# ADders - Android Ad Volume Control

## Overview

ADders lowers media volume during ads and fades it in/out.

## Android

See `ADders/ADders/` folder for Android project.

### Requirements
- minSdk 21 (Android 5.0+)
- targetSdk 35 (Android 15+)
- Edge-to-edge support on Android 15+
- No Google Play Services dependency
- No native code

### Features
- Volume lower during ads (0-100% slider)
- Fade in/out (OFF or 3-15s)
- Detects volume differences between models
- Fixed volume detection
- Remote playback detection (casting)
- OEM battery optimization handling
- Legacy launcher icons (mipmap-*dpi)
- Works on 320dp width, tablets, foldables, 200% font, RTL
- Black theme

### Build
```bash
cd ADders/ADders
./gradlew test lint assembleDebug
```

## iOS

See `ios/ADdersPlayer/` folder.

**Hard limit**: iOS cannot detect other apps' playback or change system volume. Ad muting is impossible.

iOS player provides:
- Local audio file player
- Same fade settings (OFF + 3-15s)
- In-app volume slider
- Background audio support
- Lock screen controls
- iOS 15 deployment target

### Build
```bash
cd ios/ADdersPlayer
swift test
xcodebuild test -scheme ADdersPlayer -destination "platform=iOS Simulator,name=iPhone 15"
```

## Device Checklist

| Model | Android/iOS | Tested |
|-------|-------------|--------|
| Samsung Galaxy S24 | 15 | Yes |
| Google Pixel 8 | 15 | Yes |
| Xiaomi Redmi Note 13 | 14 | Yes |
| OnePlus 12 | 14 | Yes |
| Huawei Mate 60 | 13 | Yes |
| iPhone 15 | iOS 17 | Yes |
| iPad Pro 12.9 | iOS 17 | Yes |
