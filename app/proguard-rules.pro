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

# JavaMail (IMAP) uses reflection for providers and content handlers.
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.mail.** { *; }
-keep class com.sun.activation.** { *; }
-keep class myjava.awt.datatransfer.** { *; }
-keep class org.apache.harmony.** { *; }
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.security.**
-dontwarn javax.activation.**
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn com.sun.activation.**
-dontwarn myjava.awt.datatransfer.**
-dontwarn org.apache.harmony.**
-dontwarn sun.security.**

# PLAN 9 B1: libphonenumber + geocoder read their tables as resources next to their classes.
-keep class com.google.i18n.phonenumbers.** { *; }
-dontwarn com.google.i18n.phonenumbers.**
