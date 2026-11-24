# SwipeClean ⚡

An ultra-fast, native Android photo and video cleaner engineered for modern devices and high-refresh-rate displays.

[![Android CI](https://github.com/notUbaid/SwipeClean/actions/workflows/ci.yml/badge.svg)](https://github.com/notUbaid/SwipeClean/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/notUbaid/SwipeClean)](https://github.com/notUbaid/SwipeClean/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-green.svg)](https://android.com)
[![Target SDK](https://img.shields.io/badge/Target_SDK-35_(Android_15)-blue.svg)](https://developer.android.com)

---

## Features

- **120Hz Gesture Deck**: Smooth swipe physics with velocity dampening, rotation, and magnetic haptic feedback.
  - **Swipe Right**: Keep media (remembered persistently).
  - **Swipe Left**: Stage for deletion.
  - **Swipe Up**: Bookmark / Decide later.
- **Chronological Undo Engine**: Step backwards through actions instantly to correct accidental swipes.
- **Safe 7-Day Bin**: Trashed items are held safely with live countdown badges before permanent removal.
- **Pre-Deletion Review**: Review staged deletions with selective toggles before committing to the bin.
- **Month-by-Month Progress**: Visual circular progress wheels showing reviewed status and storage metrics across your library.
- **On This Day Memories**: Quickly review and curate photos taken on today's calendar date from previous years.
- **Smart Quick Clean**: Dedicated filters for Similar & Burst shots, Large Videos, and Screenshots.
- **High-Performance Media Viewing**:
  - Fullscreen photo viewing with 1x–5x pinch-to-zoom and double-tap zoom, safely downsampled for high-resolution cameras.
  - In-card looping video preview with interactive timeline frame scrubber and audio mute toggle.
- **AMOLED Pitch Black Theme**: Pure black interface (`#070709`) to minimize power consumption on OLED displays.
- **Privacy-First & Offline**: Completely on-device. No telemetry, no ads, no cloud sync, and no network access required.

---

## Tech Stack & Architecture

- **Language**: Kotlin 2.0
- **UI Framework**: Jetpack Compose (BOM 2024.11.00)
- **Image Pipeline**: Coil 2.7.0 (with hardware video frame decoding)
- **Video Engine**: AndroidX Media3 ExoPlayer 1.4.1
- **Local Persistence**: SQLite with custom thread-safe helper
- **Android Target**: Android 15 (compileSdk 35, targetSdk 35, minSdk 26)

---

## Building from Source

```bash
# Clone the repository
git clone https://github.com/notUbaid/SwipeClean.git
cd SwipeClean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

---

## Installation

Download the latest APK directly from the [GitHub Releases](https://github.com/notUbaid/SwipeClean/releases/latest) page, or install via ADB:

```bash
adb install -r SwipeClean-Release.apk
```
