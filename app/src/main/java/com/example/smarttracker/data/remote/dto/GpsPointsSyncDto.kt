package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.LocationPoint
import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * DTO одной GPS-точки для отправки на сервер.
 *
 * Формат даты — ISO 8601 UTC (например "2026-04-06T12:30:45.123Z").
 * Координаты — десятичные градусы (WGS84).
 *
 * @param recordedAt время записи точки в формате ISO 8601
 * @param latitude широта
 * @param longitude долгота
 * @param accuracy погрешность GPS (метры), nullable
 * @param altitude высота над уровнем моря (метры), nullable
 * @param speed скорость (м/с), nullable
 * @param calories расход калорий за интервал до этой точки (ккал), nullable.
 *   Рассчитывается на стороне Android методом MET; null если профиль пользователя
 *   (вес/рост/возраст) ещё не заполнен.
 */
data class GpsPointDto(
    @SerializedName("recorded_at")
    val recordedAt: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val speed: Float? = null,
    val calories: Double? = null,
)

/**
 * DTO запроса POST /training/{training_id}/gps_points.
 *
 * Сервер принимает не более 100 точек за один запрос.
 * batch_id — UUID батча, обеспечивает идемпотентность:
 * повторная отправка того же batchId не создаёт дубли.
 *
 * @param batchId UUID батча
 * @param points список GPS-точек (max 100)
 */
data class GpsPointsBatchRequestDto(
    @SerializedName("batch_id")
    val batchId: String,
    val points: List<GpsPointDto>,
)

/**
 * DTO ответа POST /training/{training_id}/gps_points.
 *
 * @param saved количество сохранённых точек
 * @param message человекочитаемое сообщение ("Точки сохранены")
 */
data class GpsPointsSaveResponseDto(
    val saved: Int,
    val message: String,
)

/**
 * Маппинг domain LocationPoint → GpsPointDto для отправки на сервер.
 *
 * timestampUtc (epoch millis) конвертируется в ISO 8601 UTC строку.
 * minSdk=26 → java.time доступен нативно, desugaring не нужен.
 */
fun LocationPoint.toGpsPointDto(): GpsPointDto = GpsPointDto(
    recordedAt = Instant.ofEpochMilli(timestampUtc)
        .atZone(ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    latitude   = latitude,
    longitude  = longitude,
    accuracy   = accuracy,
    altitude   = altitude,
    speed      = speed,
    calories   = calories,
)
