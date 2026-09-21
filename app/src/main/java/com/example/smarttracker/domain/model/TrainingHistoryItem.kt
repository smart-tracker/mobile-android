package com.example.smarttracker.domain.model

import java.time.LocalDate

/**
 * Элемент истории тренировки, получаемый с эндпоинта GET /training/history.
 *
 * Поля соответствуют API. Для UI нам нужна только [date] в виде [LocalDate].
 *
 * [avgHeartRate]/[maxHeartRate] — агрегаты пульса, считаются сервером при
 * save_training из GPS-точек (BR-16). null — тренировка без датчика пульса
 * или сохранена до BR-16.
 */
data class TrainingHistoryItem(
    val trainingId: String,
    val typeActivId: Int,
    val date: LocalDate,
    val timeStart: String?,
    val timeEnd: String?,
    val kilocalories: Double?,
    val distanceM: Double?,
    val avgSpeed: Double?,
    val elevationGain: Double?,
    val avgHeartRate: Double? = null,
    val maxHeartRate: Int? = null,
)

