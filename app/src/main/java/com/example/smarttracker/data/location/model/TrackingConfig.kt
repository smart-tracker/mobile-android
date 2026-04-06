package com.example.smarttracker.data.location.model

/**
 * Уровень точности / энергопотребления для запроса обновлений геолокации.
 *
 * Маппинг на платформенные константы происходит в каждой реализации [LocationTracker]:
 * - GMS: [com.google.android.gms.location.Priority]
 * - HMS: [com.huawei.hms.location.LocationRequest] PRIORITY_*
 * - AOSP: не используется явно — LocationManager не поддерживает приоритеты
 */
enum class TrackingPriority {
    /** Максимальная точность, максимальное потребление — рекомендуется для фитнес-трекинга */
    HIGH_ACCURACY,
    /** Баланс точности и батареи */
    BALANCED,
    /** Минимальное потребление — только сеть/Wi-Fi, GPS не используется */
    LOW_POWER,
}

/**
 * Конфигурация запроса обновлений геолокации.
 *
 * Используется как единая точка настройки для всех трёх провайдеров.
 * Значения по умолчанию соответствуют профилю бега из [LocationConfig].
 *
 * @param intervalMs          желаемый интервал между фиксами, мс
 * @param minDistanceMeters   минимальное смещение для триггера обновления, метры
 * @param priority            уровень точности / энергопотребления
 */
data class TrackingConfig(
    val intervalMs: Long = 2000L,
    val minDistanceMeters: Float = 5f,
    val priority: TrackingPriority = TrackingPriority.HIGH_ACCURACY,
)
