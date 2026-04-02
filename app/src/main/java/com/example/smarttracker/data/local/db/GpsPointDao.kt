package com.example.smarttracker.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с таблицей GPS-точек.
 *
 * Все suspend-функции безопасно вызываются из корутин Room автоматически
 * переключает их на IO-диспетчер. observePointsForTraining возвращает Flow,
 * который Room обновляет при любом изменении таблицы.
 */
@Dao
interface GpsPointDao {

    /** Вставить одну GPS-точку. id игнорируется (autoGenerate). */
    @Insert
    suspend fun insert(point: GpsPointEntity)

    /** Все точки тренировки (единовременный снимок). */
    @Query("SELECT * FROM gps_points WHERE trainingId = :trainingId ORDER BY timestampUtc ASC")
    suspend fun getPointsForTraining(trainingId: String): List<GpsPointEntity>

    /** Точки тренировки, ещё не отправленные на сервер. */
    @Query("SELECT * FROM gps_points WHERE trainingId = :trainingId AND isSent = 0 ORDER BY timestampUtc ASC")
    suspend fun getUnsentPoints(trainingId: String): List<GpsPointEntity>

    /**
     * Пометить все точки блока как отправленные.
     * Используется в Этапе 5 после успешной синхронизации с бэкендом.
     */
    @Query("UPDATE gps_points SET isSent = 1 WHERE batchId = :batchId")
    suspend fun markBatchAsSent(batchId: String)

    /**
     * Реактивное наблюдение за точками тренировки.
     * Room переиздаёт список при каждой записи в таблицу — удобно для обновления UI и карты.
     */
    @Query("SELECT * FROM gps_points WHERE trainingId = :trainingId ORDER BY timestampUtc ASC")
    fun observePointsForTraining(trainingId: String): Flow<List<GpsPointEntity>>
}
