# Filament / JNI
-keep class com.google.android.filament.** { *; }
-keep class com.google.android.filament.utils.** { *; }
-keepclassmembers class com.google.android.filament.** { native <methods>; }
# Keep model classes used by Room + DataStore
-keep class com.threedd.studio.data.** { *; }
-dontwarn com.google.android.filament.**
