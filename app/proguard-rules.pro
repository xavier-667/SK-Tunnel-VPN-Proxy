# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools.

# Preserve sing‑box JNI methods
-keep class moe.matsuri.lite.Libcore { *; }
-keepclassmembers class moe.matsuri.lite.Libcore { *; }

# Keep Kotlin metadata
-keepattributes *Annotation*, Signature, InnerClasses

# General Android rules
-keep public class * extends android.app.Service
-keep public class * extends android.app.Activity
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit