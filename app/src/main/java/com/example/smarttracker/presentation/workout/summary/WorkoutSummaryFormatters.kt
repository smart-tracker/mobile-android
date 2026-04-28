package com.example.smarttracker.presentation.workout.summary

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Чистые функции форматирования для построения [WorkoutSummaryUiState].
 *
 * Вынесены в отдельный object, потому что:
 *  - не зависят от состояния — нет смысла держать их в ViewModel-е;
 *  - используются и при сборке снимка итогов в [com.example.smarttracker.presentation.workout.start.WorkoutStartViewModel.onFinishClick],
 *    и потенциально на будущем экране истории тренировок.
 *
 * Не путать с [com.example.smarttracker.presentation.workout.start.WorkoutStartViewModel.formatPace]
 * (по скорости м/с) — он используется во время активной тренировки. Здесь дублируется только то,
 * что нужно для снимка итогов (когда уже известна суммарная дистанция и длительность).
 */
object WorkoutSummaryFormatters {

    /**
     * Форматирует unix-ms в строку "dd.MM.yyyy (День недели)" в русской локали.
     * Использует системную таймзону, чтобы дата совпадала с локальным днём пользователя.
     */
    fun formatDate(timestampMs: Long): String {
        val locale = Locale("ru")
        val date: LocalDate = Instant.ofEpochMilli(timestampMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val dateStr = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", locale))
        val dayStr = date.format(DateTimeFormatter.ofPattern("EEEE", locale))
            .replaceFirstChar { it.uppercase(locale) }
        return "$dateStr ($dayStr)"
    }

    /** "1.23 км" — два знака после запятой, точка как разделитель. */
    fun formatDistance(km: Float): String = "%.2f км".format(Locale.US, km)

    /** "12.3 м" — один знак после запятой. */
    fun formatElevation(m: Float): String = "%.1f м".format(Locale.US, m)

    /** Длительность "HH:MM:SS" с ведущими нулями. */
    fun formatDuration(elapsedMs: Long): String {
        val totalSec = elapsedMs / 1000L
        val hours   = totalSec / 3600L
        val minutes = (totalSec % 3600L) / 60L
        val seconds = totalSec % 60L
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    /**
     * Средний темп "M:SS мин/км" из дистанции (км) и длительности (мс).
     * Если дистанция == 0 или длительность == 0 — возвращает "—".
     */
    fun formatPace(distanceKm: Float, durationMs: Long): String {
        if (distanceKm <= 0f || durationMs <= 0L) return "—"
        val paceSecPerKm = (durationMs / 1000.0) / distanceKm
        val paceMin = (paceSecPerKm / 60).toInt()
        val paceSec = (paceSecPerKm % 60).toInt()
        return "$paceMin:${paceSec.toString().padStart(2, '0')} мин/км"
    }
}
