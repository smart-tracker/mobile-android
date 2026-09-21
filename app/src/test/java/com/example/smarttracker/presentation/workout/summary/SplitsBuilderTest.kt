package com.example.smarttracker.presentation.workout.summary

import com.example.smarttracker.domain.model.LocationPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты чистой логики разбивки трека на километровые сплиты [SplitsBuilder].
 *
 * Данные строятся вручную как [CumulativeTrackData] — так тесты не зависят
 * от haversine-расчётов и проверяют ровно интерполяцию границ и агрегацию.
 */
class SplitsBuilderTest {

    /** Хелпер: равномерный трек — points точек, шаг stepKm / stepMs. */
    private fun uniformTrack(points: Int, stepKm: Float, stepMs: Long): CumulativeTrackData {
        val dist = ArrayList<Float>(points)
        val time = ArrayList<Long>(points)
        for (i in 0 until points) {
            dist.add(i * stepKm)
            time.add(i * stepMs)
        }
        return CumulativeTrackData(
            distancesKm = dist,
            elevationsM = List(points) { 0f },
            elapsedMs = time,
            speedsMs = List(points) { 1f },
        )
    }

    @Test
    fun `равномерный трек 3 км даёт три полных сплита с одинаковым темпом`() {
        // 31 точка, шаг 0.1 км / 30 сек → 5:00 мин/км, ровно 3.0 км
        val data = uniformTrack(points = 31, stepKm = 0.1f, stepMs = 30_000L)
        val splits = SplitsBuilder.buildSplits(data)

        assertEquals(3, splits.size)
        assertEquals(listOf("1", "2", "3"), splits.map { it.label })
        splits.forEach {
            assertEquals("5:00 мин/км", it.paceDisplay)
            assertFalse(it.isPartial)
            // Все круги одинаковы → у всех максимальная относительная скорость
            assertEquals(1f, it.relativeSpeed, 0.01f)
        }
    }

    @Test
    fun `граница километра интерполируется между точками`() {
        // Две точки: 0 км @ 0 мс и 2 км @ 600_000 мс. Граница 1 км — на 300_000 мс.
        val data = CumulativeTrackData(
            distancesKm = listOf(0f, 2f),
            elevationsM = listOf(0f, 0f),
            elapsedMs = listOf(0L, 600_000L),
            speedsMs = listOf(0f, 3.33f),
        )
        val splits = SplitsBuilder.buildSplits(data)

        assertEquals(2, splits.size)
        // Оба круга по 300 сек → 5:00
        assertEquals("5:00 мин/км", splits[0].paceDisplay)
        assertEquals("5:00 мин/км", splits[1].paceDisplay)
    }

    @Test
    fun `неполный хвост попадает в список с фактическим темпом`() {
        // 1.4 км: полный км за 300 сек + хвост 0.4 км за 120 сек (тот же темп)
        val data = uniformTrack(points = 15, stepKm = 0.1f, stepMs = 30_000L)
        val splits = SplitsBuilder.buildSplits(data)

        assertEquals(2, splits.size)
        assertTrue(splits[1].isPartial)
        assertEquals("0.4", splits[1].label)
        assertEquals("5:00 мин/км", splits[1].paceDisplay)
    }

    @Test
    fun `хвост короче 50 метров отбрасывается`() {
        // 1.04 км — хвост 0.04 км < MIN_PARTIAL_KM
        val data = uniformTrack(points = 27, stepKm = 0.04f, stepMs = 12_000L)
        val splits = SplitsBuilder.buildSplits(data)

        assertEquals(1, splits.size)
        assertFalse(splits[0].isPartial)
    }

    @Test
    fun `трек короче километра даёт один неполный сплит`() {
        // 0.5 км за 150 сек
        val data = uniformTrack(points = 6, stepKm = 0.1f, stepMs = 30_000L)
        val splits = SplitsBuilder.buildSplits(data)

        assertEquals(1, splits.size)
        assertTrue(splits[0].isPartial)
        assertEquals("0.5", splits[0].label)
    }

    @Test
    fun `быстрый круг получает relativeSpeed 1, медленный — меньше`() {
        // Км 1 за 240 сек (быстрый), км 2 за 360 сек (медленный)
        val data = CumulativeTrackData(
            distancesKm = listOf(0f, 1f, 2f),
            elevationsM = listOf(0f, 0f, 0f),
            elapsedMs = listOf(0L, 240_000L, 600_000L),
            speedsMs = listOf(0f, 4f, 2.7f),
        )
        val splits = SplitsBuilder.buildSplits(data)

        assertEquals(2, splits.size)
        assertEquals(1f, splits[0].relativeSpeed, 0.001f)
        // 240/360 = 0.666…
        assertEquals(240f / 360f, splits[1].relativeSpeed, 0.001f)
    }

    @Test
    fun `синтетические таймстемпы истории (elapsed в мс) гейтятся`() {
        // История до BR-5: timestampUtc = index → elapsed последней точки = 30 мс
        val data = CumulativeTrackData(
            distancesKm = listOf(0f, 1.5f, 3f),
            elevationsM = listOf(0f, 0f, 0f),
            elapsedMs = listOf(0L, 15L, 30L),
            speedsMs = listOf(0f, 1f, 1f),
        )
        assertFalse(SplitsBuilder.hasRealTiming(data))
        assertTrue(SplitsBuilder.buildSplits(data).isEmpty())
    }

    @Test
    fun `пустые и вырожденные данные не падают`() {
        assertTrue(SplitsBuilder.buildSplits(CumulativeTrackData()).isEmpty())
        val single = CumulativeTrackData(
            distancesKm = listOf(0f),
            elevationsM = listOf(0f),
            elapsedMs = listOf(0L),
            speedsMs = listOf(0f),
        )
        assertTrue(SplitsBuilder.buildSplits(single).isEmpty())
    }

    @Test
    fun `пауза уже вычтена из elapsed — темп не включает время паузы`() {
        // Точки: 0..1 км за 300 сек, затем «пауза» уже вычтена построителем
        // cumulativeData — elapsed продолжает с 300_000. Проверяем, что buildSplits
        // доверяет elapsed как есть.
        val data = CumulativeTrackData(
            distancesKm = listOf(0f, 0.5f, 1.0f),
            elevationsM = listOf(0f, 0f, 0f),
            elapsedMs = listOf(0L, 150_000L, 300_000L),
            speedsMs = listOf(0f, 3.33f, 3.33f),
        )
        val splits = SplitsBuilder.buildSplits(data)
        assertEquals(1, splits.size)
        assertEquals("5:00 мин/км", splits[0].paceDisplay)
    }

    // ── detectPauseGapIndices: эвристика пауз для истории ────────────────────

    /** Хелпер: точка с заданным timestampUtc (координаты для эвристики не важны). */
    private fun timedPoint(tsMs: Long) = LocationPoint(
        trainingId   = "t",
        timestampUtc = tsMs,
        elapsedNanos = 0L,
        latitude     = 61.0,
        longitude    = 34.0,
        altitude     = null,
        speed        = null,
        accuracy     = null,
    )

    @Test
    fun `detectPauseGapIndices - равномерный трек без пауз даёт пустой список`() {
        // 100 точек с шагом 5 с — разрывов нет
        val points = (0 until 100).map { timedPoint(it * 5_000L) }
        assertTrue(SplitsBuilder.detectPauseGapIndices(points).isEmpty())
    }

    @Test
    fun `detectPauseGapIndices - разрыв 60с на фоне 5с интервалов - индекс первой точки после паузы`() {
        // Индексы 0..49 с шагом 5с, затем пауза 60с, дальше снова 5с
        val points = buildList {
            for (i in 0 until 50) add(timedPoint(i * 5_000L))
            val resumeBase = 49 * 5_000L + 60_000L
            for (i in 0 until 50) add(timedPoint(resumeBase + i * 5_000L))
        }
        assertEquals(listOf(50), SplitsBuilder.detectPauseGapIndices(points))
    }

    @Test
    fun `detectPauseGapIndices - синтетические таймстемпы (index) дают пустой список`() {
        // История до BR-5: timestampUtc = index → интервалы 1 мс, порог 15с недостижим
        val points = (0 until 100).map { timedPoint(it.toLong()) }
        assertTrue(SplitsBuilder.detectPauseGapIndices(points).isEmpty())
    }

    @Test
    fun `detectPauseGapIndices - меньше трёх точек - пусто`() {
        assertTrue(SplitsBuilder.detectPauseGapIndices(emptyList()).isEmpty())
        assertTrue(
            SplitsBuilder.detectPauseGapIndices(listOf(timedPoint(0L), timedPoint(90_000L))).isEmpty()
        )
    }

    @Test
    fun `detectPauseGapIndices - редкая запись точек не принимается за паузы`() {
        // Интервал 30с (больше нижнего порога 15с!), но одинаковый у всех —
        // медианный фактор ×3 держит порог на 90с, пауз нет
        val points = (0 until 30).map { timedPoint(it * 30_000L) }
        assertTrue(SplitsBuilder.detectPauseGapIndices(points).isEmpty())
    }
}
