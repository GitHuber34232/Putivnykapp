-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
# Keep @HiltViewModel-annotated classes (annotation, not superclass)
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
# Keep @Inject constructors from being stripped by R8
-keepclasseswithmembernames class * {
    @javax.inject.Inject <init>(...);
}
-keepclasseswithmembernames class * {
    @dagger.* <methods>;
}
-dontwarn org.mapsforge.**
-dontwarn com.google.android.gms.**

# MLKit Translate — keep registrars from being stripped by R8
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_translate.** { *; }
-dontwarn com.google.mlkit.**

# WorkManager — keep InputMerger and internal classes used via reflection
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

-keep class ua.kyiv.putivnyk.data.model.** { *; }

# Keep all app classes (Hilt, Activities, Application, ViewModels)
-keep class ua.kyiv.putivnyk.** { *; }
-keep class * extends android.app.Application { *; }
# Keep Hilt-generated component trees
-keep class dagger.hilt.android.internal.** { *; }
-keep class hilt_aggregated_deps.** { *; }

# R8 missing-class suppressions (Material + MapBox R$* reference classes)
-dontwarn com.google.android.material.R$attr
-dontwarn com.google.android.material.R$dimen
-dontwarn com.google.android.material.R$string
-dontwarn com.google.android.material.R$style
-dontwarn com.google.android.material.R$styleable
-dontwarn org.maplibre.android.R$color
-dontwarn org.maplibre.android.R$drawable
