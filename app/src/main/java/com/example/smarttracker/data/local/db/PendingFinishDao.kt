package com.example.smarttracker.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO для таблицы офлайн-очереди завершений тренировок.
 *
 * Используется [SaveTrainingWorker] для чтения и удаления записей,
 * и [WorkoutStartViewModel] для записи при ошибке сети.
 */
@Dao
interface PendingFinishDao {

    /**
     * Добавить запрос завершения в очередь.
     * IGNORE: если запись с таким trainingId уже существует — не перезаписывать.
     * Это может случиться при повторном нажатии «Завершить» без сети.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PendingFinishEntity)

    /** Получить все ожидающие запросы для обработки воркером. */
    @Query("SELECT * FROM pending_finishes")
    suspend fun getAll(): List<PendingFinishEntity>

    /** Получить запрос для конкретной тренировки (или null, если его нет). */
    @Query("SELECT * FROM pending_finishes WHERE trainingId = :trainingId LIMIT 1")
    suspend fun getById(trainingId: String): PendingFinishEntity?

    /** Удалить успешно отправленный запрос. */
    @Query("DELETE FROM pending_finishes WHERE trainingId = :trainingId")
    suspend fun delete(trainingId: String)
}
