# Add project specific ProGuard rules here.
-keepattributes *Annotation*

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Retrofit / Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.adityaprasad.vaultdrop.data.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
