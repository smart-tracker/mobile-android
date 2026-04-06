package com.example.smarttracker.domain.model

/**
 * Результат завершения тренировки на сервере (POST /training/{id}/save_training).
 *
 * @param trainingId UUID завершённой тренировки
 * @param message человекочитаемое сообщение от сервера ("Тренировка завершена")
 */
data class SaveTrainingResult(
    val trainingId: String,
    val message: String,
)
