package com.example.smarttracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO ответа GET /training/active.
 *
 * Используется для проверки наличия незавершённой тренировки при запуске приложения.
 * Если активная тренировка существует — можно восстановить запись после краша.
 *
 * @param activeTrainingId серверный UUID тренировки
 * @param typeActivId тип активности
 * @param date дата начала (строка)
 * @param timeStart время начала (ISO 8601)
 * @param trainingTime продолжительность в секундах
 * @param isPause на паузе ли тренировка
 * @param kilocalories текущее количество сожжённых калорий
 */
data class ActiveTrainingResponseDto(
    @SerializedName("active_training_id")
    val activeTrainingId: String,
    @SerializedName("type_activ_id")
    val typeActivId: Int,
    val date: String,
    @SerializedName("time_start")
    val timeStart: String,
    @SerializedName("training_time")
    val trainingTime: Int,
    @SerializedName("is_pause")
    val isPause: Boolean,
    val kilocalories: Double,
)
