package com.example.smarttracker.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.smarttracker.domain.model.LocationPoint
import java.util.UUID

/**
 * Room-сущность для хранения GPS-точки тренировки.
 *
 * Таблица "gps_points" — намеренно плоская: каждое поле LocationPoint
 * становится столбцом. Nullable-поля хранятся как NULL в SQLite.
 *
 * **Schema v2:**
 * - Добавлено поле [bearing] (Float?) — курс движения; null при медленном движении
 * - Добавлено поле [externalId] (String?) — внешний UUID для идемпотентности батча
 *
 * **Schema v3:**
 * - Добавлено поле [calories] (Double?) — расход ккал за интервал (MET-метод)
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
    /** Курс движения [0, 360). null при скорости < MIN_SPEED_FOR_BEARING_MPS. */
    val bearing: Float?,
    /**
     * Внешний UUID для идемпотентной синхронизации (Этап 5).
     * Генерируется в [toEntity]; бэкенд отклоняет дубли по этому ключу.
     * Индексирован для быстрого поиска при bulk-отправке.
     */
    @ColumnInfo(index = true) val externalId: String?,
    val batchId: String?,
    val isSent: Boolean,
    /** Расход ккал за интервал от предыдущей точки до этой (MET-метод). null если профиль не заполнен. */
    val calories: Double? = null,
)

/** Преобразование domain-модели → Room-сущность перед сохранением. */
fun LocationPoint.toEntity(): GpsPointEntity = GpsPointEntity(
    id          = id,
    trainingId  = trainingId,
    timestampUtc = timestampUtc,
    elapsedNanos = elapsedNanos,
    latitude    = latitude,
    longitude   = longitude,
    altitude    = altitude,
    speed       = speed,
    accuracy    = accuracy,
    bearing     = bearing,
    // externalId генерируется здесь если не был задан ранее — гарантирует уникальность при batch-отправке
    externalId  = externalId ?: UUID.randomUUID().toString(),
    batchId     = batchId,
    isSent      = isSent,
    calories    = calories,
)

/** Преобразование Room-сущности → domain-модель после чтения. */
fun GpsPointEntity.toDomain(): LocationPoint = LocationPoint(
    id          = id,
    trainingId  = trainingId,
    timestampUtc = timestampUtc,
    elapsedNanos = elapsedNanos,
    latitude    = latitude,
    longitude   = longitude,
    altitude    = altitude,
    speed       = speed,
    accuracy    = accuracy,
    bearing     = bearing,
    externalId  = externalId,
    batchId     = batchId,
    isSent      = isSent,
    calories    = calories,
)
