package com.example.smarttracker.domain.model

/**
 * Результат запуска тренировки на сервере (POST /training/start).
 *
 * activeTrainingId — серверный UUID, который используется для всех
 * последующих операций: загрузка GPS-точек, завершение тренировки.
 * Этот же ID сохраняется в Room как trainingId для GPS-точек.
 *
 * @param activeTrainingId серверный UUID тренировки
 * @param typeActivId идентификатор типа активности
 * @param timeStart время начала тренировки (ISO 8601)
 * @param message человекочитаемое сообщение от сервера
 */
data class ActiveTrainingResult(
    val activeTrainingId: String,
    val typeActivId: Int,
    val timeStart: String,
    val message: String,
)
