# SwipeClean

An offline, open-source Android photo and video cleaner built with Kotlin and Jetpack Compose.

[![Android CI](https://github.com/notUbaid/SwipeClean/actions/workflows/ci.yml/badge.svg)](https://github.com/notUbaid/SwipeClean/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/notUbaid/SwipeClean)](https://github.com/notUbaid/SwipeClean/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-green.svg)](https://android.com)
[![Target SDK](https://img.shields.io/badge/Target_SDK-35_(Android_15)-blue.svg)](https://developer.android.com)

---

## Features

- **Card swipe deck**: Supports both drag distance thresholds and quick flick velocity, plus TalkBack custom actions and reduced-motion support.
  - **Swipe right**: Keep media (remembered in local SQLite).
  - **Swipe left**: Stage item for deletion.
  - **Swipe up**: Move to the Later tab to decide after speed cleaning.
- **Chronological undo**: Steps backwards cleanly through staged and persisted actions (bounded 200-entry SQLite undo stack plus in-session actions).
- **Pre-deletion review**: Grid review screen lets you untick any accidentally swiped items before moving the rest to the Bin.
- **Bin with 7-day tracking**: Items marked for deletion display live countdown badges (`7 days left`, `1 day left`, `Expired`). Files remain in your device gallery until you confirm deletion in the Bin (`Delete expired` or `Delete all`).
- **Sort & filter decks**: Filter any deck by Photos, Videos, or All media, and sort by Newest first, Oldest first, Largest first, or Smallest first — plus one-tap quick pills on Home.
- **Month-by-month progress**: Groups media using `DATE_TAKEN` (falling back to `DATE_ADDED`, then `DATE_MODIFIED`) with circular completion rings and `ContentObserver` sync when new media arrives.
- **On this day & quick clean**: Surfaces past-year memories on today's date, similar burst shots (time/aspect + 64-bit dHash), videos over 40 MB, and screenshots.
- **Fullscreen media viewer**: Pinch-to-zoom (1x–5x) and double-tap zoom for photos via Coil; looping Media3 ExoPlayer with scrubbing timeline, mute toggle, and lifecycle pause (`ON_STOP`) for videos.
- **Dark-only OLED palette**: Pure `#000000` canvas with contrast-checked neutral chrome and semantic action colors.
- **Local gallery scope**: Works directly with on-device MediaStore photos and videos (including synced items still on the device). Cloud-only Google Photos items cannot be scanned or deleted by third-party apps following Google's March 2025 Photos API scope changes.
- **Private & durable kept history**: No analytics or `INTERNET` permission. Kept right-swipes are indexed by portable content signature (`(type, byte size, filename)` SHA-256) so they survive MediaStore ID shifts, folder moves, and APK updates. App Auto Backup and device transfer (`data_extraction_rules` / `full_backup_content`) selectively back up only kept signatures (`kept_history.json`) without raw MediaStore IDs or pending deletions, `hasFragileUserData="true"` enables Android's "Keep app data" uninstall option, and Stats includes auto-syncing SAF backup export/restore.

---

## Tech Stack

- **Language**: Kotlin 2.0
- **UI**: Jetpack Compose (BOM 2024.11.00), Material 3
- **Image Loading**: Coil 2.7.0 (`coil-compose` + `coil-video`)
- **Video Playback**: AndroidX Media3 ExoPlayer 1.4.1
- **Storage**: SQLite (`reviewed_items` keyed by `(media_id, is_video)` to prevent API 26–28 ID collisions)
- **SDK**: `compileSdk 35`, `targetSdk 35`, `minSdk 26`

---

## Building from Source

```bash
git clone https://github.com/notUbaid/SwipeClean.git
cd SwipeClean

# Run unit tests, Android Lint, and build debug APK
./gradlew testDebugUnitTest lintDebug assembleDebug

# Build shrunk R8 release APK
./gradlew assembleRelease
```

### Release Signing

To sign release builds with your own keystore instead of the debug fallback key, export:

- `SWIPECLEAN_KEYSTORE_PATH`
- `SWIPECLEAN_KEYSTORE_PASSWORD`
- `SWIPECLEAN_KEY_ALIAS`
- `SWIPECLEAN_KEY_PASSWORD`

---

## Installation

Download the APK from the [GitHub Releases](https://github.com/notUbaid/SwipeClean/releases/latest) page, or install via ADB:

```bash
adb install -r SwipeClean-Release.apk
```
