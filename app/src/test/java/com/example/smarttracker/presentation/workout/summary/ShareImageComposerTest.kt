package com.example.smarttracker.presentation.workout.summary

import com.example.smarttracker.domain.model.LocationPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Тесты чистой нормализации трека [ShareImageComposer.normalizeTrack]:
 * координаты → единичный квадрат с сохранением пропорций и инверсией Y.
 */
class ShareImageComposerTest {

    private fun point(lat: Double, lon: Double) = LocationPoint(
        trainingId = "t",
        timestampUtc = 0L,
        elapsedNanos = 0L,
        latitude = lat,
        longitude = lon,
        altitude = null,
        speed = null,
        accuracy = null,
    )

    @Test
    fun `пустой и одноточечный трек — пустой результат`() {
        assertTrue(ShareImageComposer.normalizeTrack(emptyList()).isEmpty())
        assertTrue(ShareImageComposer.normalizeTrack(listOf(point(61.0, 34.0))).isEmpty())
    }

    @Test
    fun `все координаты в границах 0-1`() {
        val track = listOf(
            point(61.770, 34.350),
            point(61.775, 34.360),
            point(61.772, 34.380),
            point(61.768, 34.365),
        )
        val normalized = ShareImageComposer.normalizeTrack(track)
        assertEquals(track.size, normalized.size)
        normalized.forEach { (x, y) ->
            assertTrue("x=$x вне [0,1]", x in 0f..1f)
            assertTrue("y=$y вне [0,1]", y in 0f..1f)
        }
    }

    @Test
    fun `ось Y инвертирована — север сверху`() {
        val south = point(61.0, 34.0)
        val north = point(61.01, 34.0)
        val normalized = ShareImageComposer.normalizeTrack(listOf(south, north))
        // Северная точка (больше широта) должна иметь МЕНЬШИЙ y (выше на экране)
        assertTrue(normalized[1].second < normalized[0].second)
    }

    @Test
    fun `вытянутый по долготе трек центрируется по вертикали`() {
        // Трек — горизонтальная линия: узкий по широте, широкий по долготе
        val track = listOf(point(61.0, 34.0), point(61.0, 34.1))
        val normalized = ShareImageComposer.normalizeTrack(track)
        // Обе точки по y в середине квадрата
        normalized.forEach { (_, y) -> assertEquals(0.5f, y, 0.01f) }
        // По x — растянуты на всю ширину
        assertEquals(0f, normalized[0].first, 0.01f)
        assertEquals(1f, normalized[1].first, 0.01f)
    }

    @Test
    fun `пропорции сохраняются с учётом cos-коррекции долготы`() {
        // На широте 60° градус долготы вдвое короче градуса широты (cos 60° = 0.5).
        // Трек 0.1° по долготе и 0.05° по широте на этой широте — квадрат.
        val track = listOf(point(60.0, 34.0), point(60.05, 34.1))
        val normalized = ShareImageComposer.normalizeTrack(track)
        val dx = abs(normalized[1].first - normalized[0].first)
        val dy = abs(normalized[1].second - normalized[0].second)
        // Диагональ реального квадрата остаётся диагональю квадрата
        assertEquals(dx, dy, 0.03f)
    }

    @Test
    fun `вырожденный трек (все точки в одном месте) — пустой результат`() {
        val track = List(5) { point(61.0, 34.0) }
        assertTrue(ShareImageComposer.normalizeTrack(track).isEmpty())
    }
}
