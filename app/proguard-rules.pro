# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# Keep OkHttp classes
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Gson classes
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data classes and model classes
-keep class com.example.comicreader.data.** { *; }
-keep class com.example.comicreader.model.** { *; }

# Keep TTS related classes
-keep class android.speech.tts.** { *; }
-keep interface android.speech.tts.** { *; }

# Keep archive extraction libraries
-keep class net.lingala.zip4j.** { *; }
-keep class com.github.junrar.** { *; }
-dontwarn net.lingala.zip4j.**
-dontwarn com.github.junrar.**

# Keep PhotoView library
-keep class com.github.chrisbanes.photoview.** { *; }

# Keep ViewBinding classes
-keep class * extends androidx.viewbinding.ViewBinding {
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static *** bind(android.view.View);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# SLF4J rules
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.impl.StaticLoggerBinder
-keep class org.slf4j.impl.StaticLoggerBinder { *; }

# Apache Commons Compress rules
-dontwarn org.apache.commons.compress.**
-keep class org.apache.commons.compress.** { *; }

# XZ for Java rules
-dontwarn org.tukaani.xz.**
-keep class org.tukaani.xz.** { *; }

# Optimize and obfuscate
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Keep crash reporting
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# Security: Remove debug information
-printmapping mapping.txt
-printseeds seeds.txt
-printusage usage.txt
# Keep Tesseract OCR classes (Phase 3: Dual-engine OCR)
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-keepclassmembers class com.googlecode.tesseract.android.** { *; }
-dontwarn com.googlecode.tesseract.**
-dontwarn com.googlecode.leptonica.**

# Keep Encrypted SharedPreferences (Phase 2: Secure API key storage)
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn androidx.security.crypto.**
-dontwarn com.google.crypto.tink.**

# Keep Sherpa-ONNX TTS classes (on-device narrator)
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**
