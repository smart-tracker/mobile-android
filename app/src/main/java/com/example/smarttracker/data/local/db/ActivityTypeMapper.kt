package com.example.smarttracker.data.local.db

import com.example.smarttracker.data.local.IconCacheManager
import com.example.smarttracker.data.remote.dto.ActivityTypeDto
import com.example.smarttracker.domain.model.WorkoutType

/**
 * Маппер между слоями для видов активности.
 *
 * Живёт в data-слое: использует IconCacheManager (data) и WorkoutType (domain).
 * Domain-слой остаётся чистым — Room-импортов в нём нет.
 */

/** ActivityTypeEntity → WorkoutType. iconFile разрешается через IconCacheManager в момент чтения. */
fun ActivityTypeEntity.toDomain(iconCache: IconCacheManager): WorkoutType = WorkoutType(
    id       = id,
    name     = name,
    iconKey  = id.toString(),
    iconFile = iconCache.getCached(id),
    imageUrl = imagePath,
)

/** ActivityTypeDto → ActivityTypeEntity для upsert после сетевого ответа. */
fun ActivityTypeDto.toEntity(): ActivityTypeEntity = ActivityTypeEntity(
    id        = id,
    name      = name,
    imagePath = imagePath,
)
