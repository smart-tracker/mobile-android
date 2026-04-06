package com.example.smarttracker.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с таблицей GPS-точек.
 *
 * [insert] и [insertAll] используют [OnConflictStrategy.IGNORE]: если точка с таким
 * же первичным ключом уже существует (редкий кейс при crash-recovery), запись тихо
 * игнорируется без исключения — дубли не накапливаются.
 *
 * [insertAll] принимает батч и вставляет в одной транзакции Room — эффективнее
 * N отдельных [insert] при сбросе буфера из [LocationTrackingService].
 */
@Dao
interface GpsPointDao {

    /** Вставить одну GPS-точку. id игнорируется (autoGenerate). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(point: GpsPointEntity)

    /**
     * Batch-вставка списка точек в одной транзакции.
     * Используется при сбросе in-memory буфера из LocationTrackingService.
     * IGNORE: при повторной отправке (crash-recovery) дубли не создаются.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(points: List<GpsPointEntity>)

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

    /**
     * Последняя сохранённая GPS-точка из предыдущих тренировок.
     * Используется для начального центрирования карты до получения GPS-сигнала.
     *
     * [excludedTrainingId] позволяет исключить активную discovery/черновую тренировку,
     * чтобы метод не возвращал точку текущей временной сессии как "последнюю из прошлых".
     * Если [excludedTrainingId] == null, поведение остаётся прежним.
     *
     * Возвращает null если после применения фильтра подходящих тренировок ещё не было.
     */
    @Query("""
        SELECT * FROM gps_points
        WHERE (:excludedTrainingId IS NULL OR trainingId != :excludedTrainingId)
        ORDER BY timestampUtc DESC LIMIT 1
    """)
    suspend fun getLastPoint(excludedTrainingId: String?): GpsPointEntity?
}
