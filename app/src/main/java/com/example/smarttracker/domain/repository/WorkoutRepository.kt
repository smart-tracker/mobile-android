package com.example.smarttracker.domain.repository

import com.example.smarttracker.domain.model.ActiveTrainingResult
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.domain.model.SaveTrainingResult
import com.example.smarttracker.domain.model.WorkoutType

/**
 * Контракт репозитория тренировок.
 *
 * Объединяет справочные данные (типы активностей) и операции
 * жизненного цикла тренировки: старт → GPS-синхронизация → завершение.
 */
interface WorkoutRepository {
    /** Возвращает список доступных типов тренировок */
    suspend fun getWorkoutTypes(): Result<List<WorkoutType>>

    /**
     * Начать тренировку на сервере.
     * Возвращает серверный UUID, который используется для GPS-загрузки и завершения.
     *
     * @param typeActivId идентификатор типа активности
     */
    suspend fun startTraining(typeActivId: Int): Result<ActiveTrainingResult>

    /**
     * Завершить тренировку на сервере.
     * Фиксирует время окончания и итоговую статистику.
     *
     * @param trainingId серверный UUID тренировки
     * @param timeEnd время окончания (ISO 8601 UTC)
     * @param totalDistanceMeters общая дистанция в метрах (nullable)
     * @param totalKilocalories общие калории (nullable)
     */
    suspend fun saveTraining(
        trainingId: String,
        timeEnd: String,
        totalDistanceMeters: Double?,
        totalKilocalories: Double?,
    ): Result<SaveTrainingResult>

    /**
     * Загрузить батч GPS-точек на сервер.
     * Максимум 100 точек за запрос (ограничение API).
     *
     * @param trainingId серверный UUID тренировки
     * @param batchId UUID батча для идемпотентности
     * @param points список GPS-точек для отправки
     * @return количество сохранённых на сервере точек
     */
    suspend fun uploadGpsPoints(
        trainingId: String,
        batchId: String,
        points: List<LocationPoint>,
    ): Result<Int>
}
