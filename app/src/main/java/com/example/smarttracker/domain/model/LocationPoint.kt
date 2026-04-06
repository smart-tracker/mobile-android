package com.example.smarttracker.domain.model

/**
 * Одна GPS-точка, записанная во время тренировки.
 *
 * Намеренно не содержит импортов android.* — domain-слой остаётся чистым Kotlin.
 * Поля altitude, speed, accuracy nullable, потому что Android Location не гарантирует
 * их наличие: hasAltitude(), hasSpeed(), hasAccuracy() могут вернуть false.
 */
data class LocationPoint(
    val id: Long = 0,
    val trainingId: String,         // UUID тренировки
    val timestampUtc: Long,         // epoch millis (из Location.time)
    val elapsedNanos: Long,         // монотонные часы (из Location.elapsedRealtimeNanos)
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,          // null если hasAltitude() == false
    val speed: Float?,              // null если hasSpeed() == false, единицы: м/с
    val accuracy: Float?,           // null если hasAccuracy() == false, единицы: метры
    /**
     * Курс движения в градусах [0, 360). null если скорость < MIN_SPEED_FOR_BEARING_MPS
     * или hasBearing() == false — при медленном движении компас шумит.
     */
    val bearing: Float? = null,
    /**
     * Внешний идентификатор точки для идемпотентной синхронизации с бэкендом (Этап 5).
     * UUID генерируется при создании точки, сохраняется в Room.
     * Позволяет бэкенду отклонять дубли при повторной отправке одного батча.
     */
    val externalId: String? = null,
    val batchId: String? = null,    // UUID блока для idempotency (заполняется в Этапе 5)
    val isSent: Boolean = false
)
