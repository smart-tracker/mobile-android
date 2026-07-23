package com.example.smarttracker.presentation.calendar

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
}
