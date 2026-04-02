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
    val batchId: String? = null,    // UUID блока для idempotency (заполняется в Этапе 5)
    val isSent: Boolean = false
)
