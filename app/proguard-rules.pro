# Keep Room entities
-keep class com.roox.mcqquiz.data.model.** { *; }
# Keep Gemini AI SDK
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**
# Keep Google Sign-In
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
