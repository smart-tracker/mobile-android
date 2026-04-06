package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.SaveTrainingResult
import com.google.gson.annotations.SerializedName

/**
 * DTO запроса POST /training/{training_id}/save_training.
 *
 * Завершает тренировку на сервере и фиксирует итоговые показатели.
 * time_end — ISO 8601 UTC. distance и calories — nullable,
 * бэкенд может рассчитать их самостоятельно из GPS-точек.
 *
 * @param timeEnd время окончания тренировки (ISO 8601)
 * @param totalDistanceMeters общая дистанция в метрах
 * @param totalKilocalories общее количество сожжённых килокалорий
 */
data class TrainingSaveRequestDto(
    @SerializedName("time_end")
    val timeEnd: String,
    @SerializedName("total_distance_meters")
    val totalDistanceMeters: Double? = null,
    @SerializedName("total_kilocalories")
    val totalKilocalories: Double? = null,
)

/**
 * DTO ответа POST /training/{training_id}/save_training.
 *
 * @param trainingId UUID завершённой тренировки
 * @param message человекочитаемое сообщение ("Тренировка завершена")
 */
data class TrainingSaveResponseDto(
    @SerializedName("training_id")
    val trainingId: String,
    val message: String,
)

/** Маппинг DTO → domain-модель */
fun TrainingSaveResponseDto.toDomain(): SaveTrainingResult = SaveTrainingResult(
    trainingId = trainingId,
    message    = message,
)
