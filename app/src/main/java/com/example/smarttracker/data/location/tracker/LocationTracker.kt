package com.example.smarttracker.data.location.tracker

import com.example.smarttracker.data.location.model.TrackLocation
import com.example.smarttracker.data.location.model.TrackingConfig

/**
 * Контракт источника геолокации, не зависящий от конкретного SDK.
 *
 * Три реализации:
 * - [AospLocationTracker] — стандартный android.location.LocationManager
 * - [GmsLocationTracker]  — Google FusedLocationProviderClient
 * - [HmsLocationTracker]  — Huawei Location Kit
 *
 * Выбор реализации происходит в [LocationTrackerFactory] на основе
 * результата [RuntimeDetector.detect].
 */
interface LocationTracker {

    /**
     * Начать получение обновлений геолокации.
     *
     * Вызов безопасен если разрешения уже выданы (проверка происходит
     * на уровне UI в LocationPermissionHandler). Повторный вызов без
     * предварительного [stopTracking] добавляет новую подписку.
     *
     * @param config      конфигурация запроса (интервал, точность)
     * @param onLocation  callback с новой точкой; может вызываться на любом потоке
     */
    fun startTracking(config: TrackingConfig, onLocation: (TrackLocation) -> Unit)

    /**
     * Остановить получение обновлений геолокации.
     *
     * Безопасен для повторного вызова и вызова без предшествующего [startTracking].
     */
    fun stopTracking()

    /**
     * Запросить последнюю известную позицию.
     *
     * Результат может быть устаревшим — использовать только как начальное
     * приближение до получения первого фикса через [startTracking].
     *
     * @param callback  вызывается с [TrackLocation] или null если позиция недоступна
     */
    fun getLastKnownLocation(callback: (TrackLocation?) -> Unit)
}
