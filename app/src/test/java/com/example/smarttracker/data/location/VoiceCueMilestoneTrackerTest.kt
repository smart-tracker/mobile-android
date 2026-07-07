package com.example.smarttracker.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Тесты [VoiceCueMilestoneTracker]: рубежи, темп круга, интервалы, recovery. */
class VoiceCueMilestoneTrackerTest {

    private val tracker = VoiceCueMilestoneTracker()

    @Test
    fun `до первого километра объявлений нет`() {
        assertNull(tracker.onDistance(400.0, 120_000L, 1))
        assertNull(tracker.onDistance(999.0, 299_000L, 1))
    }

    @Test
    fun `первый километр объявляется с темпом от старта`() {
        val cue = tracker.onDistance(1_005.0, 300_000L, 1)
        assertEquals(1, cue?.km)
        assertEquals(300_000L, cue?.lapPaceMsPerKm)
    }

    @Test
    fun `темп второго круга считается от прошлого объявления`() {
        tracker.onDistance(1_000.0, 300_000L, 1)          // км 1 за 5:00
        val cue = tracker.onDistance(2_010.0, 660_000L, 1) // км 2 за 6:00
        assertEquals(2, cue?.km)
        assertEquals(360_000L, cue?.lapPaceMsPerKm)
    }

    @Test
    fun `между рубежами объявлений нет`() {
        tracker.onDistance(1_000.0, 300_000L, 1)
        assertNull(tracker.onDistance(1_500.0, 450_000L, 1))
        assertNull(tracker.onDistance(1_999.0, 599_000L, 1))
    }

    @Test
    fun `интервал 2 км — объявления на 2 и 4`() {
        assertNull(tracker.onDistance(1_100.0, 330_000L, 2))
        val cue2 = tracker.onDistance(2_050.0, 600_000L, 2)
        assertEquals(2, cue2?.km)
        assertEquals(300_000L, cue2?.lapPaceMsPerKm) // 600 сек / 2 км
        assertNull(tracker.onDistance(3_500.0, 1_050_000L, 2))
        assertEquals(4, tracker.onDistance(4_010.0, 1_200_000L, 2)?.km)
    }

    @Test
    fun `скачок через несколько рубежей — одно объявление последнего`() {
        // Дыра GPS: с 0.9 сразу на 3.2 км
        tracker.onDistance(900.0, 270_000L, 1)
        val cue = tracker.onDistance(3_200.0, 960_000L, 1)
        assertEquals(3, cue?.km)
        assertEquals(320_000L, cue?.lapPaceMsPerKm) // 960 сек / 3 км
        // Следующий рубеж — 4-й, без повторов пропущенных
        assertNull(tracker.onDistance(3_900.0, 1_170_000L, 1))
        assertEquals(4, tracker.onDistance(4_000.0, 1_200_000L, 1)?.km)
    }

    @Test
    fun `нулевой или отрицательный интервал выключает объявления`() {
        assertNull(tracker.onDistance(5_000.0, 1_500_000L, 0))
        assertNull(tracker.onDistance(5_000.0, 1_500_000L, -1))
    }

    @Test
    fun `restore продолжает с сохранённого рубежа`() {
        // Crash-recovery: до падения объявлено 3 км на 900-й секунде
        tracker.restore(lastAnnouncedKm = 3, lastMilestoneElapsedMs = 900_000L)
        assertNull(tracker.onDistance(3_800.0, 1_140_000L, 1))
        val cue = tracker.onDistance(4_050.0, 1_215_000L, 1)
        assertEquals(4, cue?.km)
        assertEquals(315_000L, cue?.lapPaceMsPerKm)
    }

    @Test
    fun `reset начинает отсчёт заново`() {
        tracker.onDistance(2_000.0, 600_000L, 1)
        tracker.reset()
        assertEquals(0, tracker.lastAnnouncedKm)
        val cue = tracker.onDistance(1_000.0, 240_000L, 1)
        assertEquals(1, cue?.km)
        assertEquals(240_000L, cue?.lapPaceMsPerKm)
    }
}
