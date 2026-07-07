package com.example.smarttracker.data.location

import org.junit.Assert.assertEquals
import org.junit.Test

/** Тесты [TtsPhraseFormatter]: склонения, крайние значения темпа. */
class TtsPhraseFormatterTest {

    @Test
    fun `обычный темп — минуты и секунды со склонениями`() {
        // 5:25 мин/км = 325_000 мс
        assertEquals(
            "Километр 5. Темп 5 минут 25 секунд.",
            TtsPhraseFormatter.kilometerCue(5, 325_000L),
        )
    }

    @Test
    fun `формы «одна» — 1 минута 1 секунда`() {
        assertEquals(
            "Километр 1. Темп 1 минута 1 секунда.",
            TtsPhraseFormatter.kilometerCue(1, 61_000L),
        )
    }

    @Test
    fun `формы «несколько» — 2 минуты 3 секунды`() {
        assertEquals(
            "Километр 3. Темп 2 минуты 3 секунды.",
            TtsPhraseFormatter.kilometerCue(3, 123_000L),
        )
    }

    @Test
    fun `исключение 11-14 — минут и секунд`() {
        // 11:12 = 672_000 мс
        assertEquals(
            "Километр 2. Темп 11 минут 12 секунд.",
            TtsPhraseFormatter.kilometerCue(2, 672_000L),
        )
    }

    @Test
    fun `21 — снова форма «одна» (21 секунда)`() {
        assertEquals(
            "Километр 4. Темп 4 минуты 21 секунда.",
            TtsPhraseFormatter.kilometerCue(4, 261_000L),
        )
    }

    @Test
    fun `ровные минуты — без секунд`() {
        assertEquals(
            "Километр 10. Темп 6 минут.",
            TtsPhraseFormatter.kilometerCue(10, 360_000L),
        )
    }

    @Test
    fun `темп меньше минуты — только секунды`() {
        assertEquals(
            "Километр 1. Темп 45 секунд.",
            TtsPhraseFormatter.kilometerCue(1, 45_000L),
        )
    }

    @Test
    fun `нет данных о темпе — только километр`() {
        assertEquals("Километр 3.", TtsPhraseFormatter.kilometerCue(3, 0L))
        assertEquals("Километр 3.", TtsPhraseFormatter.kilometerCue(3, -10L))
    }

    @Test
    fun `темп меньше секунды (вырожденный) — только километр`() {
        assertEquals("Километр 1.", TtsPhraseFormatter.kilometerCue(1, 500L))
    }

    @Test
    fun `часовой темп не ломает формат`() {
        // 65:05 = 3_905_000 мс
        assertEquals(
            "Километр 1. Темп 65 минут 5 секунд.",
            TtsPhraseFormatter.kilometerCue(1, 3_905_000L),
        )
    }
}
