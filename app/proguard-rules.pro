# Keep Media3 ExoPlayer reflection classes if needed
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Coil components
-dontwarn coil.**

# Keep SQLite helpers and data models
-keep class com.antigravity.swipeclean.data.model.** { *; }
