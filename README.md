# SwipeClean ⚡ (Enhanced Swipewipe for Samsung Galaxy S26 Ultra)

SwipeClean is an ultra-fast, native Android photo & video cleaner built from the ground up to replicate the best parts of **Swipewipe** and eliminate its biggest pain points (no subscriptions, no ads, true OLED dark theme, and flagship 120Hz performance).

---

## 🔍 How Swipewipe Works vs How SwipeClean Improves Upon It

| Feature / UX Element | Swipewipe (Original) | SwipeClean (Our Product) |
| :--- | :--- | :--- |
| **Monetization & Limits** | Aggressive paywall ($4.99/week or $39.99/yr), daily swipe quotas, ads. | **100% Free & Unlimited forever**. Zero subscriptions, zero paywalls, zero ads. |
| **Month-by-Month Wheels** | Shows months with circular progress rings and strike-through on completion. | **Month Progress Wheels (`HomeScreen.kt`)**: Displays exact % completed, items reviewed vs total, disk size, and green completed checkmarks. |
| **The Swipe Deck** | Fullscreen cards with swipe right to keep, left to delete. | **Spring Physics Deck (`SwipeCard.kt`)**: Fullscreen 120Hz gesture physics, velocity tracking, dynamic rotation, and vibrant KEEP / TRASH stamps. |
| **Decide Later / Bookmarks** | Swipe up to bookmark photos you aren't sure about. | **Swipe UP to Bookmark (`BookmarksScreen.kt`)**: Dedicated Bookmarks tab so tough choices don't slow down speed cleaning. |
| **"On This Day" Flashbacks** | Daily throwback to clean photos taken on this date in past years. | **Built-in "On This Day" (`HomeScreen.kt`)**: Automatically detects photos taken on today's date from 2025, 2024, 2023, etc. |
| **Pre-Deletion Review Grid** | Review deletions before finalizing to prevent accidental loss. | **Review Deletions Screen (`ReviewDeletionsScreen.kt`)**: Full review grid showing all staged deletions with individual uncheck toggles and total storage savings. |
| **Safe 7-Day Bin** | Trashed items held in system or app trash. | **7-Day Grace Bin (`BinScreen.kt`)**: Live countdown on every item (`7 days left`, `3 days left`), Restore Single/All, and permanent OS removal via MediaStore. |
| **Video Playback & Scrubbing** | Basic video playback. | **Media3 ExoPlayer (`VideoPlayerView.kt`)**: Looping auto-play, interactive timeline slider to scrub through video frames, and mute/unmute toggle. |
| **Photo Inspection & Zoom** | Standard view. | **Coil Pinch-to-Zoom (`PhotoViewer.kt`)**: 1x to 5x multi-touch pan/zoom with double-tap toggle, downsampled for 200MP Galaxy S26 Ultra camera captures without OOM crashes. |
| **Haptic Feedback** | Generic vibration or none. | **Magnetic Threshold Haptics (`SwipeCard.kt`)**: Subtle tactile tick (`TextHandleMove`) triggered when dragging past swipe thresholds on Samsung X-axis linear vibration motor. |
| **Smart Quick Clean** | Limited filtering. | **Smart Clusters (`HomeScreen.kt`)**: Instant filters for Similar & Burst shots, Large Videos (> 40MB), and Screenshots. |
| **Display Optimization** | Gray/white UI backgrounds. | **AMOLED True Black (`#070709`)**: Every black pixel is unpowered on Samsung Dynamic AMOLED 2X, maximizing battery life. |

---

## 📱 Navigation Architecture

1. **Months (Home)**:
   - Header with Clean Streak (`🔥 3 Day Streak • 4.8 GB Cleaned`).
   - "On This Day" card (memories from past years).
   - Month list with circular progress wheels (e.g. `Nov 2026: 45%`, `Oct 2026: COMPLETED ✓`).
2. **Swipe Deck**:
   - Immersive fullscreen media deck.
   - Swipe Right -> Keep.
   - Swipe Left -> Stage for Deletion.
   - Swipe Up -> Bookmark / Decide Later.
   - Tap Undo -> Instantly step backward and restore card.
   - Top-right "Review Staged (X)" button to inspect deletions anytime.
3. **Saved (Bookmarks)**:
   - Grid of items you swiped up on. Keep or Trash them with one tap when ready.
4. **Bin (7-Day Trash)**:
   - Items with countdown chips (`7 days left`, `Expires today`).
   - Reclaim storage metric (`Reclaim 3.4 GB`).
   - Restore All / Empty Bin (triggers native Samsung One UI delete confirmation).
5. **Stats & Preferences**:
   - Lifetime statistics, 7-day auto-clean toggle, and Reset Kept History option.

---

## 📦 APK File Location

- **Release APK**: [SwipeClean-Release.apk](file:///C:/Users/ubaid/.gemini/antigravity/scratch/swipeclean/SwipeClean-Release.apk) (~14.1 MB)
- **Source Directory**: [swipeclean](file:///C:/Users/ubaid/.gemini/antigravity/scratch/swipeclean)

---

## 📲 How to Install on Samsung Galaxy S26 Ultra

1. **Option A: Direct ADB Install (Fastest if connected to PC)**
   ```powershell
   adb install -r "C:\Users\ubaid\.gemini\antigravity\scratch\swipeclean\SwipeClean-Release.apk"
   ```

2. **Option B: Quick Share or USB Transfer**
   - Transfer `SwipeClean-Release.apk` to your Galaxy S26 Ultra via Quick Share, Google Drive, or USB cable.
   - Open **My Files** on your phone -> **Downloads** or **Installation files (APK)**.
   - Tap `SwipeClean-Release.apk` and tap **Install** (allow "Install Unknown Apps" if prompted).
   - Launch **SwipeClean** and tap **Grant Photos & Videos Access**.
   - Enjoy 120Hz smooth, ad-free, subscription-free photo cleaning!
