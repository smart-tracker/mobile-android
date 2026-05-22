package com.example.smarttracker.data.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Обёртка над системным API оптимизации батареи (Android M+, API 23+).
 *
 * Используется для запроса добавления приложения в Doze-whitelist, что позволяет
 * foreground-сервису получать GPS-обновления при выключенном экране без throttling.
 *
 * Без whitelist Android Doze пропускает GPS-callback'и в maintenance window'ах
 * (обычно через 5-10 мин после выключения экрана), даже несмотря на foreground-сервис
 * и удерживаемый PARTIAL_WAKE_LOCK.
 *
 * Размещён в data-слое: это обёртка системного API, domain про неё не знает.
 * Функции stateless — не требуют DI, можно вызывать напрямую из Composable/Service.
 */
object BatteryOptimizationHelper {

    /**
     * Проверяет, добавлено ли приложение в Doze-whitelist.
     *
     * На API < 23 возвращает true: Doze там отсутствует, throttling'а нет —
     * считаем что всё ок, баннер показывать не нужно.
     *
     * `minSdk=26` в проекте, поэтому ветка API < 23 на практике не выполнится,
     * но защита в helper'е делает его переиспользуемым в любом контексте.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Собирает Intent для системного диалога запроса Doze-whitelist.
     *
     * `data = package:<applicationId>` — обязательно, иначе Android покажет
     * общий список приложений, а не запрос для конкретного.
     *
     * Intent требует permission `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
     * (объявлен в AndroidManifest). Без него — SecurityException.
     */
    fun buildRequestIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
}
