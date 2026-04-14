package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.LocationPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-тесты маппера LocationPoint → GpsPointDto.
 *
 * Покрывает:
 * - recordedAt сериализуется в формат ISO 8601 UTC
 * - Nullable поля (accuracy, altitude, speed) передаются как есть
 */
class GpsPointDtoTest {

    private val samplePoint = LocationPoint(
        id            = 0L,
        trainingId    = "training-uuid-123",
        timestampUtc  = 1744531200000L,  // 2025-04-13T08:00:00.000Z
        elapsedNanos  = 123456789L,
        latitude      = 55.7558,
        longitude     = 37.6173,
        altitude      = 150.0,
        speed         = 2.5f,
        accuracy      = 5.0f,
    )

    @Test
    fun `recordedAt имеет формат ISO 8601 UTC с суффиксом Z или +00_00`() {
        val dto = samplePoint.toGpsPointDto()
        // Формат: "2025-04-13T08:00:00Z" или "2025-04-13T08:00:00+00:00"
        assertTrue(
            "recordedAt должен быть ISO-8601 UTC: ${dto.recordedAt}",
            dto.recordedAt.contains("T") && (dto.recordedAt.endsWith("Z") || dto.recordedAt.contains("+00:00"))
        )
    }

    @Test
    fun `recordedAt корректно конвертирует epoch millis`() {
        val dto = samplePoint.toGpsPointDto()
        // 1744531200000 ms = 2025-04-13T08:00:00Z
        assertTrue(
            "recordedAt должен содержать дату 2025-04-13: ${dto.recordedAt}",
            dto.recordedAt.startsWith("2025-04-13T08:00:00")
        )
    }

    @Test
    fun `latitude и longitude сохраняются без изменений`() {
        val dto = samplePoint.toGpsPointDto()
        assertEquals(55.7558, dto.latitude, 0.000001)
        assertEquals(37.6173, dto.longitude, 0.000001)
    }

    @Test
    fun `nullable поля accuracy, altitude, speed передаются`() {
        val dto = samplePoint.toGpsPointDto()
        assertEquals(5.0f, dto.accuracy)
        assertEquals(150.0, dto.altitude)
        assertEquals(2.5f, dto.speed)
    }

    @Test
    fun `null значения accuracy, altitude, speed остаются null`() {
        val point = samplePoint.copy(accuracy = null, altitude = null, speed = null)
        val dto = point.toGpsPointDto()
        assertEquals(null, dto.accuracy)
        assertEquals(null, dto.altitude)
        assertEquals(null, dto.speed)
    }

    @Test
    fun `recordedAt не null для любого timestampUtc`() {
        val dto = samplePoint.copy(timestampUtc = 0L).toGpsPointDto()
        assertNotNull(dto.recordedAt)
        assertTrue(dto.recordedAt.isNotBlank())
    }
}
