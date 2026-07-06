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

    /**
     * Минимальное смещение (метры) для OS-level LocationListener.
     * Ненулевое значение снижает количество дублирующихся точек ещё на уровне ОС.
     * Дополнительная фильтрация — [MIN_DISTANCE_ANTIJITTER_M] на уровне Service.
     */
    const val MIN_DISTANCE_M       = 5f

    /** Максимально допустимая погрешность для бега/ходьбы (метры) */
    const val MAX_ACCURACY_RUNNING = 20f

    /** Максимально допустимая погрешность для велосипеда (метры) */
    const val MAX_ACCURACY_CYCLING = 30f

    /** Через сколько мс без первого GPS-фикса показать предупреждение UNAVAILABLE */
    const val GPS_FIX_TIMEOUT_MS   = 30_000L

    /**
     * Минимальный интервал между принятыми точками (мс).
     * Слой 2 фильтрации: защищает от "burst" обновлений когда ОС присылает несколько
     * точек сразу (типично при смене провайдера GPS→NETWORK).
     */
    const val MIN_TIME_BETWEEN_UPDATES_MS = 1_000L

    /**
     * Антидребезг по расстоянию (метры).
     * Слой 4 фильтрации: исключает "прыгающие" точки вблизи одного места
     * (стоянка под деревьями, туннель, ожидание светофора).
     */
    const val MIN_DISTANCE_ANTIJITTER_M = 3f

    /**
     * Максимальная реалистичная скорость (м/с ≈ 180 км/ч).
     * Слой 3 фильтрации: отбрасывает телепортации — артефакты плохого сигнала.
     * 50 м/с соответствует ~180 км/ч — выше только скоростные трассы, не треккинг.
     */
    const val MAX_REALISTIC_SPEED_MPS = 50f

    /**
     * Порог скорости (м/с) ниже которого пеленг (bearing) считается ненадёжным.
     * При движении медленнее 0.5 м/с (≈1.8 км/ч) компас шумит — bearing сбрасывается в null.
     */
    const val MIN_SPEED_FOR_BEARING_MPS = 0.5f

    /**
     * WakeLock timeout — максимальное время удержания CPU при выключенном экране.
     * 2 часа покрывает марафон; после истечения CPU может заснуть (Doze).
     * Защита от бесконечного удержания WakeLock при краше Service.
     */
    const val WAKELOCK_TIMEOUT_MS  = 2 * 60 * 60 * 1000L   // 2 часа

    /**
     * Максимальный возраст "последней известной позиции" (мс).
     * Свежее — принимаем как актуальную позицию. Старее — игнорируем, запрашиваем новую.
     */
    const val LAST_LOCATION_MAX_AGE_MS = 60_000L   // 1 минута

    /**
     * Мягкий таймер подсказки при долгом поиске GPS.
     * Показывает hint "Выйдите на открытое место" через 10 сек, а не через 30.
     */
    const val GPS_HINT_TIMEOUT_MS  = 10_000L

    /**
     * Размер in-memory буфера GPS-точек перед записью в Room.
     * При достижении этого порога буфер сбрасывается немедленно.
     */
    const val BUFFER_FLUSH_SIZE    = 10

    /**
     * Периодический сброс буфера в Room (мс).
     * Гарантирует запись даже если [BUFFER_FLUSH_SIZE] не достигнут.
     */
    const val BUFFER_FLUSH_INTERVAL_MS = 5_000L

    /** ID foreground-уведомления — должен быть ненулевым и стабильным */
    const val NOTIFICATION_ID      = 1001

    /** ID канала уведомлений для тренировки */
    const val CHANNEL_ID           = "workout_tracking"

    /** SharedPreferences: файл для crash-recovery (хранит trainingId активной тренировки) */
    const val PREFS_RECOVERY       = "location_service_recovery"
    const val KEY_ACTIVE_TRAINING  = "active_training_id"

    /** Crash-recovery: момент старта/последнего resume тренировки (wall-clock ms).
     *  Используется как база chronometer'а в foreground-уведомлении. */
    const val KEY_SESSION_STARTED_AT     = "session_started_at"
    /** Crash-recovery: накопленное время до текущей паузы (мс). */
    const val KEY_PAUSED_ACCUMULATED_MS  = "paused_accumulated_ms"
    /** Crash-recovery: число записанных GPS-точек тренировки (для точного gap-индекса паузы). */
    const val KEY_RECORDED_POINT_COUNT   = "recorded_point_count"
    /** Crash-recovery: флаг записи на момент падения (false = тренировка была на паузе).
     *  Без него START_STICKY-рестарт молча возобновлял запись, убитую на паузе. */
    const val KEY_IS_RECORDING           = "is_recording"
    /** Crash-recovery: момент первого нажатия «Начать» (wall-clock ms), не сдвигается
     *  при resume — нужен ViewModel для даты тренировки на экране итогов. */
    const val KEY_TRAINING_STARTED_AT    = "training_started_at"
    /** Crash-recovery: heartbeat — момент последней записи состояния сервисом.
     *  ViewModel считает сессию протухшей, если heartbeat старше [RECOVERY_STALE_MS]
     *  (сервис мёртв и START_STICKY-рестарта не было). */
    const val KEY_LAST_PERSIST_AT        = "last_persist_at"
    /** Crash-recovery: gap-индексы пауз через запятую ("34,78") — для восстановления
     *  pauseGapIndices во ViewModel (иначе дистанция посчитает телепорт через паузу). */
    const val KEY_PAUSE_GAP_INDICES      = "pause_gap_indices"
    /** Crash-recovery: true = startTraining подтверждён сервером (trainingId — serverUUID).
     *  Определяет путь финиша после восстановления: прямой saveTraining или офлайн-цепочка. */
    const val KEY_IS_REGISTERED          = "is_registered_on_server"

    // Crash-recovery: профиль калорий и параметры трекинга. Без них START_STICKY-рестарт
    // терял CF/MET (calories=null до конца тренировки) и откатывал интервал/точность
    // на профиль бега даже для велотренировки.
    const val KEY_TYPE_ACTIV_ID          = "type_activ_id"
    const val KEY_WEIGHT_KG              = "weight_kg"
    const val KEY_HEIGHT_CM              = "height_cm"
    const val KEY_AGE_YEARS              = "age_years"
    const val KEY_IS_MALE                = "is_male"
    const val KEY_INTERVAL_MS            = "interval_ms"
    const val KEY_ACCURACY_THRESHOLD     = "accuracy_threshold"

    /** Порог протухания recovery-сессии: heartbeat пишется каждые
     *  [BUFFER_FLUSH_INTERVAL_MS], 2 минуты покрывают паузу START_STICKY-рестарта. */
    const val RECOVERY_STALE_MS          = 2 * 60 * 1000L

    // ── Синхронизация GPS-точек с сервером ──────────────────────────────────────

    /** Интервал между попытками отправки GPS-точек на сервер (мс) */
    const val SYNC_INTERVAL_MS     = 10_000L

    /** Максимум точек в одном батче (ограничение API POST /training/{id}/gps_points) */
    const val GPS_BATCH_MAX_SIZE   = 100
}
