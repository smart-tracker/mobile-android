package com.example.smarttracker.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Локальная очередь незавершённых запросов завершения тренировки.
 *
 * Запись создаётся когда [WorkoutStartViewModel] не смог выполнить
 * POST /training/{id}/save_training из-за отсутствия сети.
 * [SaveTrainingWorker] читает таблицу при появлении сети и доставляет запросы на сервер,
 * после чего удаляет успешно отправленные строки.
 *
 * @PrimaryKey trainingId — один UUID тренировки не может быть завершён дважды.
 * OnConflictStrategy.IGNORE в DAO гарантирует идемпотентность при повторных вставках.
 */
@Entity(tableName = "pending_finishes")
data class PendingFinishEntity(
    @PrimaryKey val trainingId: String,
    /** Время завершения в формате ISO 8601 UTC — фиксируется в момент нажатия «Завершить» */
    val timeEnd: String,
    val totalDistanceMeters: Double?,
    val totalKilocalories: Double?,
)
