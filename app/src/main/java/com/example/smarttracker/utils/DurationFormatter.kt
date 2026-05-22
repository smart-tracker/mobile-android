package com.example.smarttracker.utils

/**
 * Форматирует длительность в миллисекундах в строку "HH:MM:SS" с ведущими нулями.
 *
 * Чистая Kotlin-функция без android.* — используется и в presentation (таймер
 * тренировки), и в data (foreground-уведомление сервиса).
 *
 * Отрицательные значения трактуются как 0.
 */
fun formatHhMmSs(elapsedMs: Long): String {
    val totalSec = (elapsedMs / 1000L).coerceAtLeast(0L)
    val hours   = totalSec / 3600L
    val minutes = (totalSec % 3600L) / 60L
    val seconds = totalSec % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
