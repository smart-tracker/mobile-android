package com.example.smarttracker.domain.repository

import com.example.smarttracker.domain.model.WorkoutType

/**
 * Контракт репозитория тренировок.
 * Текущая реализация — мок. Реальная подключается через DI после готовности backend.
 */
interface WorkoutRepository {
    /** Возвращает список доступных типов тренировок */
    suspend fun getWorkoutTypes(): Result<List<WorkoutType>>
}
