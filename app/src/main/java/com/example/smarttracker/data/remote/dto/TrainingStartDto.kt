package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.ActiveTrainingResult
import com.google.gson.annotations.SerializedName

/**
 * DTO запроса POST /training/start.
 *
 * @param typeActivId идентификатор типа активности (из GET /training/types_activity)
 */
data class TrainingStartRequestDto(
    @SerializedName("type_activ_id")
    val typeActivId: Int,
)

/**
 * DTO ответа POST /training/start.
 *
 * Бэкенд создаёт запись активной тренировки и возвращает её UUID.
 * Этот UUID используется для всех последующих операций:
 * загрузка GPS-точек, завершение тренировки.
 *
 * @param activeTrainingId серверный UUID тренировки
 * @param typeActivId тип активности
 * @param timeStart время начала (ISO 8601)
 * @param message человекочитаемое сообщение ("Тренировка начата")
 */
data class TrainingStartResponseDto(
    @SerializedName("active_training_id")
    val activeTrainingId: String,
    @SerializedName("type_activ_id")
    val typeActivId: Int,
    @SerializedName("time_start")
    val timeStart: String,
    val message: String,
)

/** Маппинг DTO → domain-модель */
fun TrainingStartResponseDto.toDomain(): ActiveTrainingResult = ActiveTrainingResult(
    activeTrainingId = activeTrainingId,
    typeActivId      = typeActivId,
    timeStart        = timeStart,
    message          = message,
)
