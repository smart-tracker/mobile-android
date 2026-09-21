package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.TrainingHistoryItem
import com.google.gson.annotations.SerializedName
import java.time.LocalDate

/**
 * DTO элемента истории тренировки, соответствует JSON из GET /training/history
 */
data class TrainingHistoryResponseDto(
    @SerializedName("training_id") val trainingId: String,
    @SerializedName("type_activ_id") val typeActivId: Int,
    val date: String,
    @SerializedName("time_start") val timeStart: String?,
    @SerializedName("time_end") val timeEnd: String?,
    val kilocalories: Double?,
    @SerializedName("distance_m") val distanceM: Double?,
    @SerializedName("avg_speed") val avgSpeed: Double?,
    @SerializedName("elevation_gain") val elevationGain: Double?,
    @SerializedName("avg_heart_rate") val avgHeartRate: Double?,
    @SerializedName("max_heart_rate") val maxHeartRate: Int?,
)

/**
 * Маппер в доменную модель.
 * Парсит поле date в LocalDate; формат ожидается ISO-8601 date (yyyy-MM-dd).
 */
fun TrainingHistoryResponseDto.toDomain(): TrainingHistoryItem = TrainingHistoryItem(
    trainingId = trainingId,
    typeActivId = typeActivId,
    date = LocalDate.parse(date),
    timeStart = timeStart,
    timeEnd = timeEnd,
    kilocalories = kilocalories,
    distanceM = distanceM,
    avgSpeed = avgSpeed,
    elevationGain = elevationGain,
    avgHeartRate = avgHeartRate,
    maxHeartRate = maxHeartRate,
)

