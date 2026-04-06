# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Retrofit interfaces
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }

# Keep data classes for Gson
-keep class com.example.smarttracker.data.remote.dto.** { *; }

# GMS Location — FusedLocationProviderClient и LocationCallback
-keep class com.google.android.gms.location.** { *; }
-keep class * extends com.google.android.gms.location.LocationCallback { *; }

# HMS Location Kit — HuaweiApiAvailability, FusedLocationProviderClient и LocationCallback
-keep class com.huawei.hms.location.** { *; }
-keep class com.huawei.hms.api.** { *; }
-keep class * extends com.huawei.hms.location.LocationCallback { *; }
