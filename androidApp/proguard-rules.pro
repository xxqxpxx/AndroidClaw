# AndroidClaw ProGuard Rules

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.androidclaw.**$$serializer { *; }
-keepclassmembers class com.androidclaw.** { *** Companion; }
-keepclasseswithmembers class com.androidclaw.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Firebase Crashlytics
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class com.google.firebase.crashlytics.** { *; }

# Keep MediaPipe
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Keep Koin
-keep class org.koin.** { *; }

# Keep SQLDelight
-keep class com.androidclaw.db.** { *; }

# Keep Compose
-dontwarn androidx.compose.**

# Keep accessibility service
-keep class com.androidclaw.app.service.AutoSendAccessibilityService { *; }

# Keep admin receiver
-keep class com.androidclaw.app.admin.ClawDeviceAdminReceiver { *; }

# Keep SLF4J
-dontwarn org.slf4j.**

# Keep Porcupine
-keep class ai.picovoice.** { *; }

# Keep ONNX Runtime
-keep class com.microsoft.onnxruntime.** { *; }
-dontwarn com.microsoft.onnxruntime.**

# R8 missing class suppressions
-dontwarn javax.lang.model.**

# Keep class names for logging
-keepnames class com.androidclaw.shared.llm.ClaudeStreamEvent$* { *; }
-keepnames class com.androidclaw.shared.agent.AgentEvent$* { *; }
