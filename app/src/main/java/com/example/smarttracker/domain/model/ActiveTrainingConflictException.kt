package com.example.smarttracker.domain.model

/**
 * Исключение: сервер отклонил старт тренировки (400) потому что
 * у пользователя уже есть незавершённая активная тренировка.
 *
 * Бросается [com.example.smarttracker.data.repository.WorkoutRepositoryImpl]
 * при получении HTTP 400 от POST /training/start.
 *
 * Обрабатывается в WorkoutStartViewModel: ViewModel вызывает GET /training/active,
 * завершает orphaned-тренировку через POST /training/{id}/save_training,
 * затем повторяет старт — всё автоматически, без участия пользователя.
 */
class ActiveTrainingConflictException : Exception(
    "На сервере есть незавершённая тренировка"
)
