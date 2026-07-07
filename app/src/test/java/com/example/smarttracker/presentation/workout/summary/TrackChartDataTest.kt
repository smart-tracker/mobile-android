package com.example.smarttracker.presentation.workout.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты чистой подготовки данных графика [TrackChartData]:
 * разбиение на сегменты по паузам, сглаживание, даунсемпл.
 */
class TrackChartDataTest {

    // ── buildSegments ─────────────────────────────────────────────────────────

    @Test
    fun `без пауз — один сегмент со всеми точками`() {
        val xs = listOf(0f, 1f, 2f, 3f)
        val ys = listOf(10f, 12f, 11f, 13f)
        val segments = TrackChartData.buildSegments(xs, ys, emptySet())

        assertEquals(1, segments.size)
        assertEquals(4, segments[0].size)
        assertEquals(ChartPoint(0f, 10f), segments[0][0])
    }

    @Test
    fun `gap-индекс рвёт линию между i-1 и i`() {
        val xs = listOf(0f, 1f, 2f, 3f, 4f)
        val ys = listOf(1f, 2f, 3f, 4f, 5f)
        // Пауза между точками 1 и 2
        val segments = TrackChartData.buildSegments(xs, ys, setOf(2))

        assertEquals(2, segments.size)
        assertEquals(2, segments[0].size) // точки 0,1
        assertEquals(3, segments[1].size) // точки 2,3,4
        assertEquals(2f, segments[1][0].x, 0.001f)
    }

    @Test
    fun `сегмент из одной точки отбрасывается`() {
        val xs = listOf(0f, 1f, 2f)
        val ys = listOf(1f, 2f, 3f)
        // Паузы после каждой точки: сегменты [0], [1], [2] — все по одной точке
        val segments = TrackChartData.buildSegments(xs, ys, setOf(1, 2))
        assertTrue(segments.isEmpty())
    }

    @Test
    fun `рассинхрон длин массивов не падает`() {
        assertTrue(TrackChartData.buildSegments(listOf(0f, 1f), listOf(1f), emptySet()).isEmpty())
    }

    // ── smooth ────────────────────────────────────────────────────────────────

    @Test
    fun `сглаживание усредняет шум, x не меняется`() {
        val points = listOf(
            ChartPoint(0f, 0f),
            ChartPoint(1f, 10f),
            ChartPoint(2f, 0f),
            ChartPoint(3f, 10f),
            ChartPoint(4f, 0f),
        )
        val smoothed = TrackChartData.smooth(points, window = 3)

        assertEquals(points.map { it.x }, smoothed.map { it.x })
        // Центральная точка: (10+0+10)/3
        assertEquals(20f / 3f, smoothed[2].y, 0.001f)
        // Край: усечённое окно (0+10)/2
        assertEquals(5f, smoothed[0].y, 0.001f)
    }

    @Test
    fun `плоская линия не меняется сглаживанием`() {
        val points = List(10) { ChartPoint(it.toFloat(), 7f) }
        val smoothed = TrackChartData.smooth(points, window = 5)
        smoothed.forEach { assertEquals(7f, it.y, 0.001f) }
    }

    // ── downsample ────────────────────────────────────────────────────────────

    @Test
    fun `короткий список возвращается как есть`() {
        val points = List(50) { ChartPoint(it.toFloat(), it.toFloat()) }
        assertEquals(points, TrackChartData.downsample(points, maxPoints = 200))
    }

    @Test
    fun `длинный список сжимается до maxPoints с сохранением диапазона`() {
        val points = List(2000) { ChartPoint(it.toFloat(), it.toFloat()) }
        val down = TrackChartData.downsample(points, maxPoints = 200)

        assertEquals(200, down.size)
        // Монотонность по x сохраняется
        for (i in 1 until down.size) assertTrue(down[i].x > down[i - 1].x)
        // Диапазон примерно сохраняется (бакеты усредняют края)
        assertTrue(down.first().x < 10f)
        assertTrue(down.last().x > 1985f)
    }

    // ── prepare (конвейер) ────────────────────────────────────────────────────

    @Test
    fun `prepare объединяет сегменты, сглаживание и даунсемпл`() {
        val n = 1000
        val xs = List(n) { it.toFloat() }
        val ys = List(n) { (it % 2) * 10f } // пила для сглаживания
        val segments = TrackChartData.prepare(xs, ys, setOf(500))

        assertEquals(2, segments.size)
        segments.forEach { seg ->
            assertTrue(seg.size <= TrackChartData.MAX_POINTS)
            // Пила 0/10 после сглаживания окном 5 держится около 5
            seg.drop(2).dropLast(2).forEach { p ->
                assertTrue(p.y in 3f..7f)
            }
        }
    }
}
