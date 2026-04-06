package com.example.smarttracker.data.location

import android.content.Context
import com.example.smarttracker.data.location.model.LocationRuntime

/**
 * Определяет доступную среду выполнения GPS при старте приложения.
 *
 * Порядок проверки (приоритет убывает):
 * 1. GMS — Google Play Services (большинство Android-устройств)
 * 2. HMS — Huawei Mobile Services (Huawei без GMS)
 * 3. AOSP — fallback через стандартный LocationManager
 *
 * Каждая проверка обёрнута в try-catch([NoClassDefFoundError]) — если SDK
 * отсутствует в classpath (один APK с обеими зависимостями), загрузчик классов
 * не найдёт класс и бросит именно NoClassDefFoundError, а не Exception.
 * Это позволяет безопасно компилировать с обоими SDK в одном APK.
 *
 * Результат не кэшируется намеренно — вызов один раз за жизнь Service, overhead нулевой.
 */
object RuntimeDetector {

    fun detect(context: Context): LocationRuntime {
        // ── Проверка GMS ─────────────────────────────────────────────────────
        try {
            val status = com.google.android.gms.common.GoogleApiAvailability
                .getInstance()
                .isGooglePlayServicesAvailable(context)
            if (status == com.google.android.gms.common.ConnectionResult.SUCCESS) {
                return LocationRuntime.GMS
            }
        } catch (e: NoClassDefFoundError) {
            // GMS SDK отсутствует в classpath — продолжаем к следующей проверке
        }

        // ── Проверка HMS ─────────────────────────────────────────────────────
        try {
            // HuaweiApiAvailability.isHuaweiMobileServicesAvailable возвращает int,
            // где 0 == успех (аналог GMS ConnectionResult.SUCCESS = 0)
            val status = com.huawei.hms.api.HuaweiApiAvailability
                .getInstance()
                .isHuaweiMobileServicesAvailable(context)
            if (status == 0) {
                return LocationRuntime.HMS
            }
        } catch (e: NoClassDefFoundError) {
            // HMS SDK отсутствует в classpath — используем AOSP fallback
        }

        return LocationRuntime.AOSP
    }
}
