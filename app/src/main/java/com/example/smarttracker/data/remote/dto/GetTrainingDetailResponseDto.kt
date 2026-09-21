package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.LocationPoint
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * DTO ответа GET /training/{training_id}/get_training.
 *
 * Поле [gpsTrack] — GeoJSON LineString:
 * {"type":"LineString","coordinates":[[lon,lat,alt], [lon,lat,alt], ...]}
 *
 * Координаты в порядке GeoJSON: [longitude, latitude, altitude].
 * [gpsPointsTimestamps] — параллельный массив ISO datetime (UTC) с привязкой по
 * индексу к [gpsTrack].coordinates[i]. Если массив отсутствует или короче списка
 * координат — timestampUtc для лишних точек синтезируется из индекса (старое
 * поведение). Используем [JsonElement]? чтобы Gson не падал при любом формате.
 *
 * [gpsPointsHeartRates] — параллельный массив пульса (уд/мин, BR-16), та же
 * привязка по индексу; null-элемент = в момент точки датчик не был подключён.
 * Массив отсутствует целиком у тренировок, сохранённых до BR-16.
 *
 * [elevationGain] — серверный набор высоты (метры). Приоритетнее клиентского
 * расчёта [calculateElevationGain] при показе оверлея истории.
 * [avgHeartRate]/[maxHeartRate] — серверные агрегаты пульса (BR-16); в оверлее
 * истории приоритет отдаётся значениям из GET /training/history (см.
 * showHistorySummary), эти поля — на случай будущих сценариев с деталью без
 * элемента списка.
 */
data class GetTrainingDetailResponseDto(
    @SerializedName("training_id")           val trainingId: String,
    @SerializedName("type_activ_id")         val typeActivId: Int,
    val date: String,
    @SerializedName("time_start")            val timeStart: String,
    @SerializedName("time_end")              val timeEnd: String?,
    val kilocalories: Double?,
    @SerializedName("distance_m")            val distanceM: Double?,
    @SerializedName("avg_speed")             val avgSpeed: Double?,
    @SerializedName("elevation_gain")        val elevationGain: Double?,
    @SerializedName("gps_track")             val gpsTrack: JsonElement?,
    @SerializedName("gps_points_timestamps") val gpsPointsTimestamps: List<String>?,
    @SerializedName("gps_points_heart_rates") val gpsPointsHeartRates: List<Int?>?,
    @SerializedName("avg_heart_rate")        val avgHeartRate: Double?,
    @SerializedName("max_heart_rate")        val maxHeartRate: Int?,
)

/**
 * Маппер: gpsTrack (GeoJSON LineString) → список domain LocationPoint.
 *
 * Формат coordinates: [[lon, lat, alt], ...]  (GeoJSON: longitude первым!)
 * Для каждой точки i пытаемся взять таймстемп из [gpsPointsTimestamps][i] и
 * сконвертировать в epoch ms через OffsetDateTime.toInstant.toEpochMilli.
 * Если таймстемпа нет или парсинг упал — fallback timestampUtc = index.toLong()
 * (старое поведение, чтобы scrub-маркер хотя бы шёл по порядку точек).
 *
 * Корректные timestampUtc нужны WorkoutSummaryViewModel.buildCumulativeData,
 * которое выдаёт elapsedMs[i] = points[i].timestampUtc − points[0].timestampUtc
 * — это elapsed-индикатор scrub-оверлея в истории.
 *
 * speed/accuracy не передаются сервером → null.
 */
fun GetTrainingDetailResponseDto.gpsPointsToDomain(): List<LocationPoint> {
    val element = gpsTrack ?: return emptyList()
    if (!element.isJsonObject) return emptyList()

    // Достаём массив координат из GeoJSON LineString
    val coordsElement = element.asJsonObject.get("coordinates") ?: return emptyList()
    if (!coordsElement.isJsonArray) return emptyList()

    val timestamps = gpsPointsTimestamps
    val heartRates = gpsPointsHeartRates

    return coordsElement.asJsonArray.mapIndexedNotNull { index, entry ->
        if (!entry.isJsonArray) return@mapIndexedNotNull null
        val arr = entry.asJsonArray
        if (arr.size() < 2) return@mapIndexedNotNull null
        runCatching {
            // Если есть таймстемп для этой точки — парсим в epoch ms;
            // иначе/при ошибке парсинга — fallback на индекс.
            //
            // Бэк отдаёт два формата:
            //  - "2026-05-16T08:44:00.613000Z" (со смещением)
            //  - "2026-05-18T12:04:43.705000"  (без смещения, по факту UTC)
            // OffsetDateTime требует offset → второй формат на нём падает.
            // Поэтому сначала пробуем OffsetDateTime, при провале — LocalDateTime
            // с явным UTC. Если оба упали → fallback на index.toLong().
            val tsMillis = timestamps?.getOrNull(index)?.let { raw ->
                val normalized = raw.trim().replace(' ', 'T')
                runCatching {
                    OffsetDateTime.parse(normalized)
                        .toInstant()
                        .toEpochMilli()
                }.getOrElse {
                    runCatching {
                        LocalDateTime.parse(normalized)
                            .toInstant(ZoneOffset.UTC)
                            .toEpochMilli()
                    }.getOrNull()
                }
            } ?: index.toLong()

            LocationPoint(
                trainingId   = trainingId,
                timestampUtc = tsMillis,
                elapsedNanos = index.toLong(),
                longitude    = arr[0].asDouble, // GeoJSON: lon первым
                latitude     = arr[1].asDouble,
                altitude     = if (arr.size() >= 3) arr[2].asDouble else null,
                speed        = null,
                accuracy     = null,
                // Параллельный массив BR-16: getOrNull страхует от массива
                // короче координат (сервер гарантирует равенство длин, но
                // клиент не должен падать на расхождении).
                heartRate    = heartRates?.getOrNull(index),
            )
        }.getOrNull()
    }
}
