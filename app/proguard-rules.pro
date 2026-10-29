# Room
-keep class com.dailydo.app.** { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Compose
-dontwarn androidx.compose.runtime.**
