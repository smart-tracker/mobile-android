package com.example.smarttracker.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.smarttracker.domain.model.LocationPoint

/**
 * Room-сущность для хранения GPS-точки тренировки.
 *
 * Таблица "gps_points" — намеренно плоская: каждое поле LocationPoint
 * становится столбцом. Nullable-поля (altitude, speed, accuracy, batchId)
 * хранятся как NULL в SQLite.
 */
@Entity(tableName = "gps_points")
data class GpsPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trainingId: String,
    val timestampUtc: Long,
    val elapsedNanos: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val speed: Float?,
    val accuracy: Float?,
    val batchId: String?,
    val isSent: Boolean
)

/** Преобразование domain-модели → Room-сущность перед сохранением. */
fun LocationPoint.toEntity(): GpsPointEntity = GpsPointEntity(
    id = id,
    trainingId = trainingId,
    timestampUtc = timestampUtc,
    elapsedNanos = elapsedNanos,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    speed = speed,
    accuracy = accuracy,
    batchId = batchId,
    isSent = isSent
)

/** Преобразование Room-сущности → domain-модель после чтения. */
fun GpsPointEntity.toDomain(): LocationPoint = LocationPoint(
    id = id,
    trainingId = trainingId,
    timestampUtc = timestampUtc,
    elapsedNanos = elapsedNanos,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    speed = speed,
    accuracy = accuracy,
    batchId = batchId,
    isSent = isSent
)
