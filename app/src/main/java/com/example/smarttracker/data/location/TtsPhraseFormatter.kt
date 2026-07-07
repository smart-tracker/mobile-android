package com.example.smarttracker.data.location

/**
 * Форматирование русских фраз для голосовых подсказок (TTS) во время тренировки.
 *
 * Чистый Kotlin — покрыт юнит-тестами [TtsPhraseFormatterTest]. Темп произносится
 * словами («5 минут 25 секунд»), а не «5:25»: TTS читает двоеточие
 * непредсказуемо («пять двадцать пять» / «пять целых...»).
 */
object TtsPhraseFormatter {

    /** Короткая фраза при срабатывании автопаузы. */
    const val AUTOPAUSE_CUE = "Автопауза"

    /** Короткая фраза при автоматическом продолжении записи. */
    const val RESUME_CUE = "Продолжаем"

    /**
     * Километровая подсказка: «Километр 5. Темп 5 минут 25 секунд».
     *
     * @param km             номер объявляемого километра
     * @param lapPaceMsPerKm темп последнего круга, мс на километр;
     *                       <= 0 (нет данных) → фраза без темпа
     */
    fun kilometerCue(km: Int, lapPaceMsPerKm: Long): String {
        val base = "Километр $km"
        if (lapPaceMsPerKm <= 0L) return "$base."
        val totalSec = lapPaceMsPerKm / 1000L
        val minutes = totalSec / 60L
        val seconds = totalSec % 60L
        val pace = buildString {
            if (minutes > 0L) {
                append("$minutes ${pluralRu(minutes, "минута", "минуты", "минут")}")
            }
            if (seconds > 0L) {
                if (isNotEmpty()) append(" ")
                append("$seconds ${pluralRu(seconds, "секунда", "секунды", "секунд")}")
            }
        }
        return if (pace.isEmpty()) "$base." else "$base. Темп $pace."
    }

    /**
     * Русские формы множественного числа: 1 минута / 2 минуты / 5 минут,
     * с исключением 11–14 → «минут» (11 минут, 12 секунд).
     */
    fun pluralRu(n: Long, one: String, few: String, many: String): String {
        val mod100 = n % 100
        if (mod100 in 11..14) return many
        return when (n % 10) {
            1L -> one
            2L, 3L, 4L -> few
            else -> many
        }
    }
}
