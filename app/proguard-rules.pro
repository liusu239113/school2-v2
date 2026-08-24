# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Room entities
-keep class com.arktools.xiaozhang.data.local.entity.** { *; }
-keep class com.arktools.xiaozhang.domain.model.** { *; }

# Keep Hilt components
-keepclassmembers class * {
    @dagger.hilt.android.HiltAndroidApp <methods>;
}

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep Kotlin metadata
-keepattributes RuntimeVisibleAnnotations
-keepattributes *Annotation*

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.arktools.xiaozhang.**$$serializer { *; }
-keepclassmembers class com.arktools.xiaozhang.** {
    *** Companion;
}
-keepclasseswithmembers class com.arktools.xiaozhang.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room DAOs
-keep class com.arktools.xiaozhang.data.local.dao.** { *; }

# Keep DataStore preferences keys
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Keep enums (used in Room type converters and serialization)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Bugly
-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}
