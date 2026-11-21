# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keepclassmembers enum * { *; }

-keep class com.storm3ds.emulator.domain.model.RendererConfiguration { *; }
-keep class com.storm3ds.emulator.domain.model.FirmwareConfiguration { *; }
-keep class com.storm3ds.emulator.domain.model.EmulatorConfiguration { *; }
-keep class com.storm3ds.emulator.domain.model.DldiSdCardConfiguration { *; }
-keep class com.storm3ds.emulator.domain.model.Rect { *; }
-keep class com.storm3ds.emulator.domain.model.AudioBitrate { *; }
-keep class com.storm3ds.emulator.domain.model.AudioInterpolation { *; }
-keep class com.storm3ds.emulator.domain.model.AudioLatency { *; }
-keep class com.storm3ds.emulator.domain.model.ConsoleType { *; }
-keep class com.storm3ds.emulator.domain.model.MicSource { *; }
-keep class com.storm3ds.emulator.domain.model.Cheat { *; }
-keep class com.storm3ds.emulator.domain.model.DSiWareTitle { *; }
-keep class com.storm3ds.emulator.domain.model.VideoRenderer { *; }
-keep class com.storm3ds.emulator.domain.model.VideoFiltering { *; }
-keep class com.storm3ds.emulator.domain.model.VulkanPipelineProfile { *; }
-keep class com.storm3ds.emulator.domain.model.retroachievements.RASimpleAchievement { *; }
-keep class com.storm3ds.emulator.domain.model.retroachievements.RASimpleLeaderboard { *; }
-keep class com.storm3ds.emulator.domain.model.retroachievements.RASimpleRuntimeAchievement { *; }
-keep class com.storm3ds.emulator.domain.model.retroachievements.RASimpleRuntimeAchievementBucketEntry { *; }
-keep class com.storm3ds.emulator.domain.model.retroachievements.RARuntimeBridgeConfig { *; }
-keep class com.storm3ds.emulator.domain.model.retroachievements.RARuntimeBridgeMode { *; }
-keep interface com.storm3ds.emulator.ui.emulator.render.FrameRenderCallback { *; }
-keep interface com.storm3ds.emulator.MelonRomDecryptor$DecryptProgressCallback { *; }
-keep class com.storm3ds.emulator.ui.emulator.model.VulkanPresentationConfig { *; }
-keep class com.storm3ds.emulator.domain.model.layout.BackgroundMode { *; }
-keep class com.storm3ds.emulator.ui.emulator.rewind.model.RewindSaveState { *; }
-keep class com.storm3ds.emulator.ui.emulator.rewind.model.RewindWindow { *; }
-keep class com.storm3ds.emulator.ui.settings.fragments.**
-keep class com.storm3ds.emulator.common.UriFileHandler {
    public int open(java.lang.String, java.lang.String);
}
-keep interface com.storm3ds.emulator.common.camera.DSiCameraSource { *; }

# Migration fields. These rules are required for migrations to work properly
-keep class com.storm3ds.emulator.migrations.legacy.** { *; }

# Prevent DTOs and models from being obfuscated or removed
-keep class com.storm3ds.emulator.impl.dtos.** { *; }
-keep class me.magnum.rcheevosapi.dto.** { *; }
-keep class com.storm3ds.emulator.domain.model.rom.** { *; }
-keep class com.storm3ds.emulator.domain.model.Background { *; }
-keep class com.storm3ds.emulator.domain.model.BackgroundThumbnail { *; }

# Preserve Gson annotations and field names
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ROM Icon, Thumbnail, and Processor components
-keep class com.storm3ds.emulator.impl.RomIconProvider { *; }
-keep class com.storm3ds.emulator.impl.BackgroundThumbnailProvider { *; }
-keep class com.storm3ds.emulator.impl.image.** { *; }
-keep class com.storm3ds.emulator.utils.RomProcessor { *; }
-keep class com.storm3ds.emulator.common.romprocessors.** { *; }

# Coil image loading components
-keep class coil.** { *; }
-keep class * implements coil.fetch.Fetcher { *; }
-keep class * implements coil.fetch.Fetcher$Factory { *; }
-keepclassmembers class * implements coil.fetch.Fetcher$Factory { *; }

# Google ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Google Play Services ML Kit & Vision
-keep class com.google.android.gms.vision.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.dynamite.** { *; }
-dontwarn com.google.android.gms.**
-keepclasseswithmembernames class * {
    native <methods>;
}

# Junrar, Apache Commons Compress, Tink & SLF4J
-dontwarn org.slf4j.**
-dontwarn com.github.junrar.**
-keep class com.github.junrar.** { *; }
-dontwarn org.apache.commons.compress.**
-keep class org.apache.commons.compress.** { *; }
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }


# STORM 3DS Native Bridge (Azahar JNI + custom extensions)
-keep class com.storm3ds.emulator.nativebridge.** { *; }
-keepclassmembers class com.storm3ds.emulator.nativebridge.** { *; }

# Archive extractors (.zip / .7z / .rar / .xz)
-keep class org.apache.commons.compress.** { *; }
-keep class com.github.junrar.** { *; }
-dontwarn org.apache.commons.**
-dontwarn com.github.junrar.**
