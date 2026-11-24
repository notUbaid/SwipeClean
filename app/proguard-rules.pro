# Keep Media3 ExoPlayer reflection classes if needed
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Coil decoders & ServiceLoader targets (prevents R8 stripping VideoFrameDecoder)
-keep class coil.decode.** { *; }
-keep class * implements coil.util.DecoderServiceLoaderTarget { *; }
-keep class * implements coil.util.FetcherServiceLoaderTarget { *; }
-dontwarn coil.**

# Keep SQLite helpers and data models
-keep class com.antigravity.swipeclean.data.model.** { *; }
