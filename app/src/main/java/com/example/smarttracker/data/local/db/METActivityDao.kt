package com.example.smarttracker.data.local.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * POJO для загрузки MET-активности вместе со всеми зонами скоростей.
 *
 * Используется Room [@Transaction] — гарантирует атомарное чтение обеих таблиц.
 */
data class METActivityWithZones(
    @Embedded val activity: METActivityEntity,
    @Relation(parentColumn = "typeActivId", entityColumn = "typeActivId")
    val zones: List<MetZoneEntity>,
)

/**
 * DAO для таблиц met_activities и met_zones.
 *
 * [getWithZones] — единственная точка чтения; всегда загружает активность + зоны вместе.
 * [upsertActivity] + [deleteZones] + [upsertZones] — атомарное обновление (вызывать в одной транзакции).
 */
@Dao
interface METActivityDao {

    @Transaction
    @Query("SELECT * FROM met_activities WHERE typeActivId = :typeActivId")
    suspend fun getWithZones(typeActivId: Int): METActivityWithZones?

    @Upsert
    suspend fun upsertActivity(activity: METActivityEntity)

    /** Удалить зоны перед upsert, чтобы не оставлять устаревшие записи при сокращении списка. */
    @Query("DELETE FROM met_zones WHERE typeActivId = :typeActivId")
    suspend fun deleteZones(typeActivId: Int)

    @Upsert
    suspend fun upsertZones(zones: List<MetZoneEntity>)
}
