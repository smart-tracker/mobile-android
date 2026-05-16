package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.LocationPoint
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * DTO ответа GET /training/{training_id}/get_training.
 *
 * Поле [gpsTrack] — GeoJSON LineString:
 * {"type":"LineString","coordinates":[[lon,lat,alt], [lon,lat,alt], ...]}
 *
 * Координаты в порядке GeoJSON: [longitude, latitude, altitude].
 * Временных меток нет — timestampUtc синтезируется как индекс.
 * Используем [JsonElement]? чтобы Gson не падал при любом формате.
 */
data class GetTrainingDetailResponseDto(
    @SerializedName("training_id")   val trainingId: String,
    @SerializedName("type_activ_id") val typeActivId: Int,
    val date: String,
    @SerializedName("time_start")    val timeStart: String,
    @SerializedName("time_end")      val timeEnd: String?,
    val kilocalories: Double?,
    @SerializedName("distance_m")    val distanceM: Double?,
    @SerializedName("avg_speed")     val avgSpeed: Double?,
    @SerializedName("gps_track")     val gpsTrack: JsonElement?,
)

/**
 * Маппер: gpsTrack (GeoJSON LineString) → список domain LocationPoint.
 *
 * Формат coordinates: [[lon, lat, alt], ...]  (GeoJSON: longitude первым!)
 * Временных меток нет → timestampUtc = index (порядок точек сохранён).
 * speed/accuracy не передаются сервером → null.
 */
fun GetTrainingDetailResponseDto.gpsPointsToDomain(): List<LocationPoint> {
    val element = gpsTrack ?: return emptyList()
    if (!element.isJsonObject) return emptyList()

    // Достаём массив координат из GeoJSON LineString
    val coordsElement = element.asJsonObject.get("coordinates") ?: return emptyList()
    if (!coordsElement.isJsonArray) return emptyList()

    return coordsElement.asJsonArray.mapIndexedNotNull { index, entry ->
        if (!entry.isJsonArray) return@mapIndexedNotNull null
        val arr = entry.asJsonArray
        if (arr.size() < 2) return@mapIndexedNotNull null
        runCatching {
            LocationPoint(
                trainingId   = trainingId,
                timestampUtc = index.toLong(), // временных меток нет → индекс
                elapsedNanos = index.toLong(),
                longitude    = arr[0].asDouble, // GeoJSON: lon первым
                latitude     = arr[1].asDouble,
                altitude     = if (arr.size() >= 3) arr[2].asDouble else null,
                speed        = null,
                accuracy     = null,
            )
        }.getOrNull()
    }
}
