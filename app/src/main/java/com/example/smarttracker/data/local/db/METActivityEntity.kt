package com.example.smarttracker.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room-сущность для кэширования MET-конфигурации вида активности.
 *
 * Таблица заполняется из GET /training/met/{type_activ_id} при первом обращении
 * или при истечении TTL ([cachedAt] старше 24 часов).
 * Предзагрузка запускается в фоне в [WorkoutRepositoryImpl.refreshFromNetwork].
 *
 * @param typeActivId ID вида активности — совпадает с [ActivityTypeEntity.id]
 * @param baseMet базовый MET-коэффициент (применяется когда [usesSpeedZones] == false)
 * @param usesSpeedZones нужна ли таблица скоростей для расчёта
 * @param cachedAt время загрузки (System.currentTimeMillis), используется для TTL
 */
@Entity(tableName = "met_activities")
data class METActivityEntity(
    @PrimaryKey val typeActivId: Int,
    val baseMet: Double,
    val usesSpeedZones: Boolean,
    val cachedAt: Long,
)
