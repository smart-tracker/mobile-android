package com.example.smarttracker.data.local.db

import com.example.smarttracker.data.remote.dto.METActivityResponseDto
import com.example.smarttracker.data.remote.dto.MetZoneDto
import com.example.smarttracker.domain.model.METActivity
import com.example.smarttracker.domain.model.MetZone

/**
 * Маппер между слоями для MET-данных.
 *
 * SQLite не гарантирует хранение Double.POSITIVE_INFINITY через REAL-тип,
 * поэтому для открытой верхней зоны используется Double.MAX_VALUE как сентинел.
 * Обратное преобразование: MAX_VALUE → POSITIVE_INFINITY при чтении из Room.
 */

/** METActivityWithZones (Room) → METActivity (domain) */
fun METActivityWithZones.toDomain(): METActivity = METActivity(
    baseMet        = activity.baseMet,
    usesSpeedZones = activity.usesSpeedZones,
    zones          = zones.sortedBy { it.speedMin }.map { it.toDomain() },
)

private fun MetZoneEntity.toDomain(): MetZone = MetZone(
    speedMin  = speedMin,
    // MAX_VALUE — сентинел «нет верхней границы», в domain это POSITIVE_INFINITY.
    speedMax  = if (speedMax >= Double.MAX_VALUE) Double.POSITIVE_INFINITY else speedMax,
    metValue  = metValue,
)

/** METActivityResponseDto → METActivityEntity */
fun METActivityResponseDto.toEntity(typeActivId: Int, cachedAt: Long): METActivityEntity =
    METActivityEntity(
        typeActivId    = typeActivId,
        baseMet        = baseMet,
        usesSpeedZones = usesSpeedZones,
        cachedAt       = cachedAt,
    )

/** MetZoneDto → MetZoneEntity */
fun MetZoneDto.toEntity(typeActivId: Int): MetZoneEntity = MetZoneEntity(
    typeActivId = typeActivId,
    speedMin    = speedMin,
    // null от бэкенда означает «нет верхней границы»; храним MAX_VALUE как сентинел.
    speedMax    = speedMax ?: Double.MAX_VALUE,
    metValue    = metValue,
)
