package com.example.smarttracker.data.location

/**
 * Константы конфигурации GPS-трекинга.
 *
 * Два профиля интервала: бег/ходьба vs велосипед — для разумного баланса
 * точности и энергопотребления. Порог accuracy отбрасывает "прыгающие"
 * точки в условиях плохого сигнала (многолучёвость, здания).
 */
object LocationConfig {
    /** Интервал обновлений GPS для бега и ходьбы, мс */
    const val INTERVAL_MS_RUNNING  = 3000L

    /** Интервал обновлений GPS для велосипеда, мс */
    const val INTERVAL_MS_CYCLING  = 2000L

    /** Минимальное смещение в метрах для триггера LocationListener (0 = только по времени) */
    const val MIN_DISTANCE_M       = 0f

    /** Максимально допустимая погрешность для бега/ходьбы (метры) */
    const val MAX_ACCURACY_RUNNING = 20f

    /** Максимально допустимая погрешность для велосипеда (метры) */
    const val MAX_ACCURACY_CYCLING = 30f

    /** Через сколько мс без первого GPS-фикса показать предупреждение UNAVAILABLE */
    const val GPS_FIX_TIMEOUT_MS   = 30_000L

    /** ID foreground-уведомления — должен быть ненулевым и стабильным */
    const val NOTIFICATION_ID      = 1001

    /** ID канала уведомлений для тренировки */
    const val CHANNEL_ID           = "workout_tracking"
}
