# Keep Kotlin metadata
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Keep AndroidX
-keep class androidx.** { *; }

# Keep app classes
-keep class com.hallal.solver.** { *; }

# Keep AccessibilityService (required by Android)
-keep class * extends android.accessibilityservice.AccessibilityService { *; }

# Keep JSON parsing (org.json is built-in, no rule needed)

# Suppress warnings for missing classes that are not used at runtime
-dontwarn kotlin.reflect.jvm.internal.**
