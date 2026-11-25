# Keep annotations & generic signatures required by Retrofit, Moshi, Room & Kotlinx Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlinx Serialization (type-safe Navigation Compose routes, Room enums, JSON backup)
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.ui.navigation.**$$serializer { *; }
-keepclassmembers class com.example.ui.navigation.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.example.data.local.entity.**$$serializer { *; }
-keep,includedescriptorclasses class com.example.domain.model.**$$serializer { *; }
-keep class com.example.ui.navigation.PantryDestination
-keep class * implements com.example.ui.navigation.PantryDestination

# Moshi KSP-generated adapters
-keep class * extends com.squareup.moshi.JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-keep @com.squareup.moshi.JsonClass class com.example.data.remote.** { *; }

# Retrofit interfaces
-keep interface com.example.data.remote.** { *; }

# App receivers and workers referenced in AndroidManifest / WorkManager
-keep class com.example.data.repository.AppUpdateInstallReceiver { *; }
-keep class com.example.data.worker.BootCompletedReceiver { *; }
-keep class com.example.data.worker.ExpiryNotificationWorker { *; }
-keep class com.example.data.worker.AppUpdateCheckWorker { *; }

# Suppress optional R8 warnings from unused transitive references
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.**

# Strip verbose/debug logs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

