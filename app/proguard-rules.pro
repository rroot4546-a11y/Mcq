# Keep Room entities
-keep class com.roox.mcqquiz.data.model.** { *; }
# Keep Google Sign-In
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
