# Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.saydlawy.sahab.data.**$$serializer { *; }
-keepclassmembers class com.saydlawy.sahab.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.saydlawy.sahab.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }

# Keep Room entities/DAOs, and WorkManager workers (Serializable models kept above)
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# OkHttp & Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# AndroidX Security Crypto / Tink
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**

# Crashlytics readable stacktraces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Media3 / ExoPlayer (rely on library consumer-proguard rules; suppress optional missing refs)
-dontwarn androidx.media3.**

# AndroidX AppCompat (consumer rules embedded in AAR)
-dontwarn androidx.appcompat.**

# Consolidated DataStore & Firebase DataStorage keep rules
-keep class androidx.datastore.preferences.** { *; }
-keep class androidx.datastore.core.** { *; }
-keep class com.google.firebase.datastorage.** { *; }
-dontwarn androidx.datastore.**
-dontwarn com.google.firebase.datastorage.**
