# R8 rules for the release build.
#
# Skyline Glider uses no reflection, no serialization libraries and no JNI, so
# the default Android + Compose rules cover almost everything. What follows is
# defensive: keep the entry point, and strip logging from the shipped binary.

# Entry point.
-keep class com.skyline.glider.MainActivity { *; }

# Compose ships its own consumer rules via the AndroidX artifacts; these
# suppress noisy warnings from optional metadata that isn't packaged.
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlinx.coroutines.**

# Keep enum values() / valueOf() — used by the game's state machines.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Drop debug logging from the release binary.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Line numbers in crash reports, without leaking original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
