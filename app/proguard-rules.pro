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

# HMS Location ссылается на классы других HMS-китов (NetworkKit, HiAnalytics,
# EMUI BuildEx, libcore), которых нет в зависимостях — на устройствах Huawei
# они приходят с HMS Core. R8 сыпет "Missing class" warning'ами — глушим,
# чтобы не маскировали реальные проблемы в логе сборки.
-dontwarn com.huawei.hianalytics.**
-dontwarn com.huawei.hms.network.**
-dontwarn com.huawei.hms.commonkit.**
-dontwarn com.huawei.android.**
-dontwarn com.huawei.libcore.**
# EMUI-класс в android.* namespace (есть только на Huawei-прошивках)
-dontwarn android.telephony.HwTelephonyManager

# Gson — TypeToken использует generic-сигнатуры через рефлексию.
# Без keep R8 стирает параметры типов → десериализация List<T> возвращает
# LinkedTreeMap вместо DTO (падает ClassCastException в рантайме, не в компиляции).
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepattributes AnnotationDefault

# MapLibre — JNI-мост: нативный код вызывает Java-методы по именам.
# Обфускация имён ломает вызовы из C++ → крэш при открытии карты.
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

# Вырезать debug-логи из release-байткода (работает только с
# proguard-android-optimize.txt — при выключенной оптимизации правило игнорируется).
# Log.w/Log.e оставлены: пользовательский logcat при баг-репорте — единственный
# канал диагностики до подключения крашрепортинга.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
