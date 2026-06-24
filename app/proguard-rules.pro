# Keep Kotlin metadata needed by reflection on Android.
-keep class com.zai.vmccues.** { *; }

# Google Play Services Activity Recognition — keep the classes we call by name
# so they survive minification in release builds.
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**
