package com.example.smarttracker.presentation.calendar

import com.example.smarttracker.domain.model.TrainingHistoryItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Юнит-тесты чистых функций бесконечного скролла истории: заголовки периодов и
 * индексация (все периоды идут подряд → индекс = календарное смещение от сегодня).
 */
class CalendarFormattersTest {

    // 2026-07-23 — четверг; 2026-07-20 — понедельник той недели.
    private val today = LocalDate.of(2026, 7, 23)

    // ── Заголовки ────────────────────────────────────────────────────────────

    @Test
    fun `dayHeaderLabel — дата с коротким днём недели`() {
        assertEquals("22.07.2026, ср", dayHeaderLabel(LocalDate.of(2026, 7, 22)))
        assertEquals("23.07.2026, чт", dayHeaderLabel(today))
    }

    @Test
    fun `weekHeaderLabel — диапазон, конец с годом`() {
        assertEquals("20.07 – 26.07.2026", weekHeaderLabel(LocalDate.of(2026, 7, 20)))
    }

    @Test
    fun `monthHeaderLabel — русский месяц с заглавной + год`() {
        assertEquals("Июль 2026", monthHeaderLabel(LocalDate.of(2026, 7, 1)))
        assertEquals("Декабрь 2025", monthHeaderLabel(LocalDate.of(2025, 12, 1)))
    }

    // ── periodStartOf ────────────────────────────────────────────────────────

    @Test
    fun `periodStartOf — приведение к началу периода`() {
        val d = LocalDate.of(2026, 7, 22) // среда
        assertEquals(d, periodStartOf(HistoryViewMode.DAY, d))
        assertEquals(LocalDate.of(2026, 7, 20), periodStartOf(HistoryViewMode.WEEK, d))
        assertEquals(LocalDate.of(2026, 7, 1), periodStartOf(HistoryViewMode.MONTH, d))
    }

    // ── periodCount ──────────────────────────────────────────────────────────

    @Test
    fun `periodCount — дни включительно`() {
        assertEquals(4, periodCount(HistoryViewMode.DAY, LocalDate.of(2026, 7, 20), today))
    }

    @Test
    fun `periodCount — недели включительно`() {
        // первая неделя 06.07 (пн), текущая 20.07 (пн) → 06,13,20 = 3
        assertEquals(3, periodCount(HistoryViewMode.WEEK, LocalDate.of(2026, 7, 6), today))
    }

    @Test
    fun `periodCount — месяцы включительно, через год`() {
        assertEquals(3, periodCount(HistoryViewMode.MONTH, LocalDate.of(2026, 5, 15), today))
        // ноя 2025 → июль 2026 = 9 месяцев включительно
        assertEquals(9, periodCount(HistoryViewMode.MONTH, LocalDate.of(2025, 11, 30), today))
    }

    @Test
    fun `periodCount — пустая история (first = today) даёт 1`() {
        assertEquals(1, periodCount(HistoryViewMode.DAY, today, today))
        assertEquals(1, periodCount(HistoryViewMode.WEEK, today, today))
        assertEquals(1, periodCount(HistoryViewMode.MONTH, today, today))
    }

    // ── periodStartAt / periodIndexOf ────────────────────────────────────────

    @Test
    fun `periodStartAt — смещение от сегодня (0 = сегодня)`() {
        assertEquals(today, periodStartAt(HistoryViewMode.DAY, today, 0))
        assertEquals(LocalDate.of(2026, 7, 21), periodStartAt(HistoryViewMode.DAY, today, 2))
        assertEquals(LocalDate.of(2026, 7, 13), periodStartAt(HistoryViewMode.WEEK, today, 1))
        assertEquals(LocalDate.of(2026, 6, 1), periodStartAt(HistoryViewMode.MONTH, today, 1))
    }

    @Test
    fun `periodIndexOf — индекс периода по дате`() {
        assertEquals(0, periodIndexOf(HistoryViewMode.DAY, today, today))
        assertEquals(3, periodIndexOf(HistoryViewMode.DAY, LocalDate.of(2026, 7, 20), today))
        assertEquals(2, periodIndexOf(HistoryViewMode.MONTH, LocalDate.of(2026, 5, 10), today))
    }

    @Test
    fun `round-trip — periodStartAt по индексу возвращает начало периода даты`() {
        val date = LocalDate.of(2026, 5, 10)
        for (mode in HistoryViewMode.values()) {
            val idx = periodIndexOf(mode, date, today)
            assertEquals(periodStartOf(mode, date), periodStartAt(mode, today, idx))
        }
    }

    // ── Фильтр и сортировка строчной раскладки ───────────────────────────────

    /** id, тип, дата, старт, конец, ккал, дистанция(м), ср.скорость, набор высоты. */
    private fun item(
        id: String,
        typeId: Int,
        date: LocalDate,
        startHour: Int = 8,
        durationMin: Long = 30,
        kcal: Double? = 300.0,
        distanceM: Double? = 5000.0,
    ) = TrainingHistoryItem(
        id, typeId, date,
        "%sT%02d:00:00+00:00".format(date, startHour),
        "%sT%02d:%02d:00+00:00".format(date, startHour + (durationMin / 60), durationMin % 60),
        kcal, distanceM, 2.5, 20.0,
    )

    private val run1 = item("r1", 1, LocalDate.of(2026, 7, 20), durationMin = 30, distanceM = 5000.0, kcal = 300.0)
    private val bike = item("b1", 3, LocalDate.of(2026, 7, 21), durationMin = 50, distanceM = 15000.0, kcal = 500.0)
    private val run2 = item("r2", 1, LocalDate.of(2026, 7, 22), durationMin = 10, distanceM = null, kcal = null)

    private val all = listOf(run1, bike, run2)

    @Test
    fun `filterByTypes — пустой набор возвращает все`() {
        assertEquals(all, filterByTypes(all, emptySet()))
    }

    @Test
    fun `filterByTypes — мультивыбор оставляет только выбранные виды`() {
        assertEquals(listOf(run1, run2), filterByTypes(all, setOf(1)))
        assertEquals(all, filterByTypes(all, setOf(1, 3)))
        assertEquals(emptyList<TrainingHistoryItem>(), filterByTypes(all, setOf(99)))
    }

    @Test
    fun `sortTrainings — по дате в обе стороны`() {
        assertEquals(
            listOf(run2, bike, run1),
            sortTrainings(all, HistorySort.DATE, asc = false).map { it }
        )
        assertEquals(listOf(run1, bike, run2), sortTrainings(all, HistorySort.DATE, asc = true))
    }

    @Test
    fun `sortTrainings — по длительности`() {
        // bike 50 мин > run1 30 мин > run2 10 мин
        assertEquals(listOf(bike, run1, run2), sortTrainings(all, HistorySort.DURATION, asc = false))
        assertEquals(listOf(run2, run1, bike), sortTrainings(all, HistorySort.DURATION, asc = true))
    }

    @Test
    fun `sortTrainings — null-метрика всегда в конце независимо от направления`() {
        // run2 без дистанции и калорий → в конце и при asc, и при desc.
        assertEquals(run2, sortTrainings(all, HistorySort.DISTANCE, asc = false).last())
        assertEquals(run2, sortTrainings(all, HistorySort.DISTANCE, asc = true).last())
        assertEquals(run2, sortTrainings(all, HistorySort.CALORIES, asc = false).last())
        assertEquals(run2, sortTrainings(all, HistorySort.CALORIES, asc = true).last())
    }

    @Test
    fun `sortTrainings — по дистанции и калориям среди значений`() {
        assertEquals(listOf(bike, run1), sortTrainings(all, HistorySort.DISTANCE, asc = false).take(2))
        assertEquals(listOf(run1, bike), sortTrainings(all, HistorySort.DISTANCE, asc = true).take(2))
        assertEquals(listOf(bike, run1), sortTrainings(all, HistorySort.CALORIES, asc = false).take(2))
    }

    @Test
    fun `sortLabel — русские подписи для всех метрик`() {
        assertEquals("По дате", sortLabel(HistorySort.DATE))
        assertEquals("По длительности", sortLabel(HistorySort.DURATION))
        assertEquals("По дистанции", sortLabel(HistorySort.DISTANCE))
        assertEquals("По калориям", sortLabel(HistorySort.CALORIES))
    }
}
