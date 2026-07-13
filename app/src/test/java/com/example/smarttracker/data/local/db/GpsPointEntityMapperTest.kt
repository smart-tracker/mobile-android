package com.example.smarttracker.data.local.db

import com.example.smarttracker.domain.model.LocationPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Round-trip тесты мапперов LocationPoint ↔ GpsPointEntity.
 *
 * Гарантируют, что ни одно поле (включая добавленный в v9 heartRate)
 * не теряется на пути domain → Room → domain.
 */
class GpsPointEntityMapperTest {

    private val samplePoint = LocationPoint(
        id           = 5L,
        trainingId   = "training-uuid-123",
        timestampUtc = 1744531200000L,
        elapsedNanos = 123456789L,
        latitude     = 61.7849,
        longitude    = 34.3469,
        altitude     = 150.0,
        speed        = 2.5f,
        accuracy     = 5.0f,
        bearing      = 270.5f,
        externalId   = "ext-uuid",
        batchId      = "batch-uuid",
        isSent       = true,
        calories     = 0.42,
        heartRate    = 148,
    )

    @Test
    fun `round-trip сохраняет все поля включая heartRate`() {
        val restored = samplePoint.toEntity().toDomain()
        assertEquals(samplePoint, restored)
    }

    @Test
    fun `null heartRate переживает round-trip`() {
        val restored = samplePoint.copy(heartRate = null).toEntity().toDomain()
        assertEquals(null, restored.heartRate)
    }

    @Test
    fun `toEntity генерирует externalId если он не задан`() {
        val entity = samplePoint.copy(externalId = null).toEntity()
        assertNotNull("externalId обязан появиться для идемпотентности батчей", entity.externalId)
    }
}
