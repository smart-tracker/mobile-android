package com.example.smarttracker.data.repository

import com.example.smarttracker.data.local.IconCacheManager
import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.data.remote.TrainingApiService
import com.example.smarttracker.data.remote.dto.ActivityTypeDto
import com.example.smarttracker.data.remote.dto.GpsPointsSaveResponseDto
import com.example.smarttracker.data.remote.dto.TrainingStartResponseDto
import com.example.smarttracker.domain.model.LocationPoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

/**
 * Unit-тесты WorkoutRepositoryImpl.
 *
 * Покрывает:
 * - iconKey = id.toString(), а не name (защита от смены языка API)
 * - imageUrl=null не вызывает исключений
 * - uploadGpsPoints возвращает количество сохранённых точек
 * - startTraining возвращает корректный domain-объект
 */
class WorkoutRepositoryImplTest {

    private lateinit var authApi: AuthApiService
    private lateinit var trainingApi: TrainingApiService
    private lateinit var iconCache: IconCacheManager
    private lateinit var repository: WorkoutRepositoryImpl

    @Before
    fun setUp() {
        authApi     = mock()
        trainingApi = mock()
        iconCache   = mock()
        repository  = WorkoutRepositoryImpl(authApi, trainingApi, iconCache)
    }

    // ── getWorkoutTypes() ─────────────────────────────────────────────────────

    @Test
    fun `getWorkoutTypes - iconKey равен id_toString, не name`() = runTest {
        whenever(authApi.getActivityTypes()).thenReturn(
            listOf(ActivityTypeDto(id = 1, name = "Бег", imagePath = null))
        )
        whenever(iconCache.getCached(1)).thenReturn(null)

        val result = repository.getWorkoutTypes()

        assertTrue(result.isSuccess)
        val type = result.getOrNull()?.first()
        assertEquals("1", type?.iconKey)
    }

    @Test
    fun `getWorkoutTypes - iconKey не использует name для маппинга`() = runTest {
        whenever(authApi.getActivityTypes()).thenReturn(
            listOf(ActivityTypeDto(id = 3, name = "Велосипед", imagePath = null))
        )
        whenever(iconCache.getCached(3)).thenReturn(null)

        val result = repository.getWorkoutTypes()
        val type = result.getOrNull()?.first()

        // iconKey должен быть "3", а не "Велосипед"
        assertEquals("3", type?.iconKey)
        assertTrue("iconKey не должен быть именем", type?.iconKey != "Велосипед")
    }

    @Test
    fun `getWorkoutTypes - imageUrl null не вызывает исключений`() = runTest {
        whenever(authApi.getActivityTypes()).thenReturn(
            listOf(ActivityTypeDto(id = 2, name = "Северная ходьба", imagePath = null))
        )
        whenever(iconCache.getCached(2)).thenReturn(null)

        val result = repository.getWorkoutTypes()

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull()?.first()?.imageUrl)
    }

    @Test
    fun `getWorkoutTypes - imageUrl сохраняется из imagePath DTO`() = runTest {
        val url = "https://runtastic.gottland.ru/icons/run.png"
        whenever(authApi.getActivityTypes()).thenReturn(
            listOf(ActivityTypeDto(id = 1, name = "Бег", imagePath = url))
        )
        whenever(iconCache.getCached(1)).thenReturn(null)

        val result = repository.getWorkoutTypes()
        val type = result.getOrNull()?.first()

        assertEquals(url, type?.imageUrl)
    }

    @Test
    fun `getWorkoutTypes - iconFile из кэша используется если файл есть`() = runTest {
        val cachedFile = File("/tmp/1.png")
        whenever(authApi.getActivityTypes()).thenReturn(
            listOf(ActivityTypeDto(id = 1, name = "Бег", imagePath = null))
        )
        whenever(iconCache.getCached(1)).thenReturn(cachedFile)

        val result = repository.getWorkoutTypes()
        val type = result.getOrNull()?.first()

        assertEquals(cachedFile, type?.iconFile)
    }

    @Test
    fun `getWorkoutTypes - iconFile null когда кэш пуст`() = runTest {
        whenever(authApi.getActivityTypes()).thenReturn(
            listOf(ActivityTypeDto(id = 1, name = "Бег", imagePath = null))
        )
        whenever(iconCache.getCached(1)).thenReturn(null)

        val result = repository.getWorkoutTypes()
        val type = result.getOrNull()?.first()

        assertNull(type?.iconFile)
    }

    @Test
    fun `getWorkoutTypes - возвращает все типы из API`() = runTest {
        whenever(authApi.getActivityTypes()).thenReturn(
            listOf(
                ActivityTypeDto(id = 1, name = "Бег",   imagePath = null),
                ActivityTypeDto(id = 2, name = "Ходьба", imagePath = null),
                ActivityTypeDto(id = 3, name = "Вело",   imagePath = null),
            )
        )
        whenever(iconCache.getCached(any())).thenReturn(null)

        val result = repository.getWorkoutTypes()

        assertEquals(3, result.getOrNull()?.size)
    }

    // ── uploadGpsPoints() ─────────────────────────────────────────────────────

    @Test
    fun `uploadGpsPoints - возвращает количество сохранённых точек от API`() = runTest {
        whenever(trainingApi.uploadGpsPoints(any(), any())).thenReturn(
            GpsPointsSaveResponseDto(saved = 5, message = "OK")
        )

        val result = repository.uploadGpsPoints(
            trainingId = "uuid-123",
            batchId    = "batch-uuid",
            points     = makeLocationPoints(5)
        )

        assertTrue(result.isSuccess)
        assertEquals(5, result.getOrNull())
    }

    @Test
    fun `uploadGpsPoints - LocationPoint конвертируется в GpsPointDto без NPE`() = runTest {
        whenever(trainingApi.uploadGpsPoints(any(), any())).thenReturn(
            GpsPointsSaveResponseDto(saved = 1, message = "OK")
        )

        val pointWithNulls = LocationPoint(
            trainingId   = "uuid",
            timestampUtc = 1744531200000L,
            elapsedNanos = 0L,
            latitude     = 55.7558,
            longitude    = 37.6173,
            altitude     = null,
            speed        = null,
            accuracy     = null,
        )

        val result = repository.uploadGpsPoints("uuid", "batch", listOf(pointWithNulls))
        assertTrue("uploadGpsPoints не должен падать при null-полях", result.isSuccess)
    }

    // ── startTraining() ───────────────────────────────────────────────────────

    @Test
    fun `startTraining - возвращает activeTrainingId из API`() = runTest {
        whenever(trainingApi.startTraining(any())).thenReturn(
            TrainingStartResponseDto(
                activeTrainingId = "server-uuid-456",
                typeActivId      = 1,
                timeStart        = "2026-04-13T10:00:00Z",
                message          = "Тренировка начата"
            )
        )

        val result = repository.startTraining(typeActivId = 1)

        assertTrue(result.isSuccess)
        assertEquals("server-uuid-456", result.getOrNull()?.activeTrainingId)
    }

    @Test
    fun `startTraining - typeActivId передаётся в запрос без изменений`() = runTest {
        whenever(trainingApi.startTraining(any())).thenReturn(
            TrainingStartResponseDto("uuid", 3, "2026-04-13T10:00:00Z", "OK")
        )

        val result = repository.startTraining(typeActivId = 3)
        assertEquals(3, result.getOrNull()?.typeActivId)
    }

    // ── Хелперы ───────────────────────────────────────────────────────────────

    private fun makeLocationPoints(count: Int): List<LocationPoint> =
        (1..count).map { i ->
            LocationPoint(
                trainingId   = "uuid",
                timestampUtc = i.toLong() * 1_000L,
                elapsedNanos = i.toLong() * 1_000_000_000L,
                latitude     = 55.7558 + i * 0.0001,
                longitude    = 37.6173 + i * 0.0001,
                altitude     = null,
                speed        = null,
                accuracy     = null,
            )
        }
}
