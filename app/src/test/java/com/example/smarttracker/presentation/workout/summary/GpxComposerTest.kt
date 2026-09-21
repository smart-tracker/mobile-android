package com.example.smarttracker.presentation.workout.summary

import com.example.smarttracker.domain.model.LocationPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-тесты GpxComposer.buildGpx — чистая сборка XML без Android.
 *
 * Покрывает:
 * - каркас GPX 1.1 (версия, creator, оба xmlns);
 * - разбиение на <trkseg> по pauseGapIndices;
 * - опциональные узлы: <ele> (null altitude), gpxtpx:hr (null heartRate),
 *   <time> (includeTime=false у истории с синтетическими таймстемпами);
 * - экранирование XML-спецсимволов в имени трека.
 */
class GpxComposerTest {

    private fun point(
        index: Int,
        heartRate: Int? = null,
        altitude: Double? = 10.0,
    ) = LocationPoint(
        trainingId   = "t-1",
        timestampUtc = 1_753_088_400_000L + index * 5_000L, // 2025-07-21T09:00:00Z + 5с/точка
        elapsedNanos = index * 5_000_000_000L,
        latitude     = 61.774 + index * 0.001,
        longitude    = 34.379,
        altitude     = altitude,
        speed        = null,
        accuracy     = null,
        heartRate    = heartRate,
    )

    @Test
    fun `каркас - GPX 1-1, creator и оба namespace`() {
        val gpx = GpxComposer.buildGpx("Бег", listOf(point(0)), emptyList(), includeTime = true)

        assertTrue(gpx.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertTrue(gpx.contains("""version="1.1""""))
        assertTrue(gpx.contains("""creator="SmartTracker""""))
        assertTrue(gpx.contains("""xmlns="http://www.topografix.com/GPX/1/1""""))
        assertTrue(gpx.contains("xmlns:gpxtpx="))
        assertTrue(gpx.contains("<name>Бег</name>"))
        assertTrue(gpx.contains("""<trkpt lat="61.774" lon="34.379">"""))
        assertTrue(gpx.trimEnd().endsWith("</gpx>"))
    }

    @Test
    fun `паузы разбивают трек на сегменты по gap-индексам`() {
        // 4 точки, resume на индексе 2 → сегменты [0,1] и [2,3]
        val gpx = GpxComposer.buildGpx(
            "Бег", (0..3).map { point(it) }, pauseGapIndices = listOf(2), includeTime = false,
        )

        assertEquals(2, Regex("<trkseg>").findAll(gpx).count())
        assertEquals(2, Regex("</trkseg>").findAll(gpx).count())
        // Точка индекса 2 (lat 61.776) открывает второй сегмент — сразу после закрытия первого
        val secondSegment = gpx.substringAfter("</trkseg>")
        assertTrue(secondSegment.substringAfter("<trkseg>").trimStart().startsWith("<trkpt lat=\"61.776"))
    }

    @Test
    fun `includeTime=false - нет ни metadata-time, ни time у точек`() {
        val gpx = GpxComposer.buildGpx("Бег", listOf(point(0)), emptyList(), includeTime = false)
        assertFalse(gpx.contains("<time>"))
    }

    @Test
    fun `includeTime=true - metadata и точки содержат ISO-время UTC`() {
        val gpx = GpxComposer.buildGpx("Бег", listOf(point(0)), emptyList(), includeTime = true)
        assertTrue(gpx.contains("<metadata><time>2025-07-21T09:00:00Z</time></metadata>"))
        assertTrue(gpx.contains("<time>2025-07-21T09:00:00Z</time>"))
    }

    @Test
    fun `пульс пишется расширением gpxtpx только у точек с heartRate`() {
        val gpx = GpxComposer.buildGpx(
            "Бег",
            listOf(point(0, heartRate = 150), point(1, heartRate = null)),
            emptyList(),
            includeTime = false,
        )
        assertEquals(1, Regex("<gpxtpx:hr>").findAll(gpx).count())
        assertTrue(gpx.contains("<gpxtpx:hr>150</gpxtpx:hr>"))
    }

    @Test
    fun `ele пишется только у точек с altitude`() {
        val gpx = GpxComposer.buildGpx(
            "Бег",
            listOf(point(0, altitude = 12.5), point(1, altitude = null)),
            emptyList(),
            includeTime = false,
        )
        assertEquals(1, Regex("<ele>").findAll(gpx).count())
        assertTrue(gpx.contains("<ele>12.5</ele>"))
    }

    @Test
    fun `спецсимволы в имени трека экранируются`() {
        val gpx = GpxComposer.buildGpx("Бег & <прогулка>", listOf(point(0)), emptyList(), includeTime = false)
        assertTrue(gpx.contains("<name>Бег &amp; &lt;прогулка&gt;</name>"))
        assertFalse(gpx.contains("<name>Бег & <прогулка></name>"))
    }

    @Test
    fun `пустой список точек - валидный каркас без trkseg`() {
        val gpx = GpxComposer.buildGpx("Бег", emptyList(), emptyList(), includeTime = true)
        assertFalse(gpx.contains("<trkseg>"))
        assertTrue(gpx.contains("<trk>"))
        assertTrue(gpx.trimEnd().endsWith("</gpx>"))
    }
}
