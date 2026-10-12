# Orbit data classes are (de)serialized with kotlinx.serialization.
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**

-keep,includedescriptorclasses class io.github.thelony97.orbit.**$$serializer { *; }
-keepclassmembers class io.github.thelony97.orbit.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.thelony97.orbit.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Navigation 3 keys are restored via serialization.
-keep class io.github.thelony97.orbit.ui.Route** { *; }

# Broadcast receivers, workers and widget receiver are referenced from the manifest / WorkManager.
-keep class io.github.thelony97.orbit.** extends android.content.BroadcastReceiver { *; }
-keep class io.github.thelony97.orbit.** extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
