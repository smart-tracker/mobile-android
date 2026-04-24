package com.example.smarttracker.domain.model

/**
 * Сервер вернул HTTP 4xx на POST /training/{id}/save_training.
 *
 * Означает что тренировка уже закрыта (например auto-recovery успел
 * завершить её раньше) или не существует на сервере.
 * Запись из pending_finishes можно безопасно удалять — retry бессмысленен.
 *
 * Бросается [com.example.smarttracker.data.repository.WorkoutRepositoryImpl]
 * при получении HTTP 400–499 от POST /training/{id}/save_training.
 */
class TrainingAlreadyClosedException(val httpCode: Int) :
    Exception("Тренировка уже закрыта (HTTP $httpCode)")
