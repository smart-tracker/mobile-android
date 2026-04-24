package com.example.smarttracker.data.repository

import com.example.smarttracker.data.local.IconCacheManager
import com.example.smarttracker.data.local.db.ActivityTypeDao
import com.example.smarttracker.data.local.db.ActivityTypeEntity
import com.example.smarttracker.data.local.db.PendingFinishDao
import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.data.remote.TrainingApiService
import com.example.smarttracker.data.remote.dto.GpsPointsSaveResponseDto
import com.example.smarttracker.data.remote.dto.TrainingSaveResponseDto
import com.example.smarttracker.data.remote.dto.TrainingStartResponseDto
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.domain.model.NetworkUnavailableException
import com.example.smarttracker.domain.model.TrainingAlreadyClosedException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException

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
    private lateinit var activityTypeDao: ActivityTypeDao
    private lateinit var pendingFinishDao: PendingFinishDao
    private lateinit var repository: WorkoutRepositoryImpl

    @Before
    fun setUp() {
        authApi          = mock()
        trainingApi      = mock()
        iconCache        = mock()
        activityTypeDao  = mock()
        pendingFinishDao = mock()
        // refreshFromNetwork запускается в фоне через downloadScope.launch;
        // runCatching перехватывает любые ошибки тихо, поэтому authApi можно не стабить.
        repository = WorkoutRepositoryImpl(authApi, trainingApi, iconCache, activityTypeDao, pendingFinishDao)
    }

    // ── workoutTypesFlow() ────────────────────────────────────────────────────

    @Test
    fun `workoutTypesFlow - iconKey равен id_toString, не name`() = runTest {
        whenever(activityTypeDao.observeAll()).thenReturn(
            flowOf(listOf(ActivityTypeEntity(id = 1, name = "Бег", imagePath = null)))
        )
        whenever(iconCache.getCached(1)).thenReturn(null)

        val types = repository.workoutTypesFlow().first()
        val type = types.first()

        assertEquals("1", type.iconKey)
    }

    @Test
    fun `workoutTypesFlow - iconKey не использует name для маппинга`() = runTest {
        whenever(activityTypeDao.observeAll()).thenReturn(
            flowOf(listOf(ActivityTypeEntity(id = 3, name = "Велосипед", imagePath = null)))
        )
        whenever(iconCache.getCached(3)).thenReturn(null)

        val types = repository.workoutTypesFlow().first()
        val type = types.first()

        // iconKey должен быть "3", а не "Велосипед"
        assertEquals("3", type.iconKey)
        assertTrue("iconKey не должен быть именем", type.iconKey != "Велосипед")
    }

    @Test
    fun `workoutTypesFlow - imageUrl null не вызывает исключений`() = runTest {
        whenever(activityTypeDao.observeAll()).thenReturn(
            flowOf(listOf(ActivityTypeEntity(id = 2, name = "Северная ходьба", imagePath = null)))
        )
        whenever(iconCache.getCached(2)).thenReturn(null)

        val types = repository.workoutTypesFlow().first()

        assertNull(types.first().imageUrl)
    }

    @Test
    fun `workoutTypesFlow - imageUrl сохраняется из imagePath entity`() = runTest {
        val url = "https://runtastic.gottland.ru/icons/run.png"
        whenever(activityTypeDao.observeAll()).thenReturn(
            flowOf(listOf(ActivityTypeEntity(id = 1, name = "Бег", imagePath = url)))
        )
        whenever(iconCache.getCached(1)).thenReturn(null)

        val types = repository.workoutTypesFlow().first()

        assertEquals(url, types.first().imageUrl)
    }

    @Test
    fun `workoutTypesFlow - iconFile из кэша используется если файл есть`() = runTest {
        val cachedFile = File("/tmp/1.png")
        whenever(activityTypeDao.observeAll()).thenReturn(
            flowOf(listOf(ActivityTypeEntity(id = 1, name = "Бег", imagePath = null)))
        )
        whenever(iconCache.getCached(1)).thenReturn(cachedFile)

        val types = repository.workoutTypesFlow().first()

        assertEquals(cachedFile, types.first().iconFile)
    }

    @Test
    fun `workoutTypesFlow - iconFile null когда кэш пуст`() = runTest {
        whenever(activityTypeDao.observeAll()).thenReturn(
            flowOf(listOf(ActivityTypeEntity(id = 1, name = "Бег", imagePath = null)))
        )
        whenever(iconCache.getCached(1)).thenReturn(null)

        val types = repository.workoutTypesFlow().first()

        assertNull(types.first().iconFile)
    }

    @Test
    fun `workoutTypesFlow - возвращает все типы из DAO`() = runTest {
        whenever(activityTypeDao.observeAll()).thenReturn(
            flowOf(listOf(
                ActivityTypeEntity(id = 1, name = "Бег",    imagePath = null),
                ActivityTypeEntity(id = 2, name = "Ходьба", imagePath = null),
                ActivityTypeEntity(id = 3, name = "Вело",   imagePath = null),
            ))
        )
        whenever(iconCache.getCached(any())).thenReturn(null)

        val types = repository.workoutTypesFlow().first()

        assertEquals(3, types.size)
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

    // ── saveTraining() — маппинг исключений (Fix 1 + 4) ─────────────────────

    /**
     * Успешный ответ → Result.success с корректным trainingId.
     */
    @Test
    fun `saveTraining success - возвращает SaveTrainingResult`() = runTest {
        whenever(trainingApi.saveTraining(any(), any())).thenReturn(
            TrainingSaveResponseDto("training-uuid", "Тренировка завершена")
        )

        val result = repository.saveTraining("training-uuid", "2026-04-24T10:00:00Z", null, null)

        assertTrue(result.isSuccess)
        assertEquals("training-uuid", result.getOrNull()?.trainingId)
    }

    /**
     * IOException (нет сети) → NetworkUnavailableException.
     * Это ключевой инвариант: только эта ошибка попадает в offline-очередь.
     *
     * Почему thenAnswer а не thenThrow:
     * Mockito считает IOException «checked exception» и запрещает thenThrow,
     * если метод не объявляет @Throws в Java-сигнатуре. suspend-функции Kotlin
     * компилируются без throws-клауз. thenAnswer { throw ... } обходит эту проверку.
     */
    @Test
    fun `saveTraining IOException - бросает NetworkUnavailableException`() = runTest {
        whenever(trainingApi.saveTraining(any(), any()))
            .thenAnswer { throw IOException("Connection refused") }

        val result = repository.saveTraining("training-uuid", "2026-04-24T10:00:00Z", null, null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NetworkUnavailableException)
    }

    /**
     * HTTP 400 → TrainingAlreadyClosedException с httpCode=400.
     * Типичная ситуация: бэкенд вернул 400, тренировка уже закрыта или не существует.
     */
    @Test
    fun `saveTraining HttpException 400 - бросает TrainingAlreadyClosedException`() = runTest {
        whenever(trainingApi.saveTraining(any(), any())).thenThrow(httpException(400))

        val result = repository.saveTraining("training-uuid", "2026-04-24T10:00:00Z", null, null)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is TrainingAlreadyClosedException)
        assertEquals(400, (exception as TrainingAlreadyClosedException).httpCode)
    }

    /**
     * HTTP 404 → тоже TrainingAlreadyClosedException (тренировка не найдена).
     * Весь диапазон 4xx трактуется одинаково: retry бессмысленен.
     */
    @Test
    fun `saveTraining HttpException 404 - бросает TrainingAlreadyClosedException с кодом 404`() = runTest {
        whenever(trainingApi.saveTraining(any(), any())).thenThrow(httpException(404))

        val result = repository.saveTraining("training-uuid", "2026-04-24T10:00:00Z", null, null)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is TrainingAlreadyClosedException)
        assertEquals(404, (exception as TrainingAlreadyClosedException).httpCode)
    }

    /**
     * HTTP 500 → пробрасывается как HttpException, НЕ оборачивается в доменный тип.
     * WorkManager воркер поймает его в ветке else → retry с backoff.
     */
    @Test
    fun `saveTraining HttpException 500 - пробрасывается как HttpException без обёртки`() = runTest {
        whenever(trainingApi.saveTraining(any(), any())).thenThrow(httpException(500))

        val result = repository.saveTraining("training-uuid", "2026-04-24T10:00:00Z", null, null)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        // 5xx НЕ становится NetworkUnavailableException — не ставим в offline-очередь
        assertTrue(exception !is NetworkUnavailableException)
        assertTrue(exception !is TrainingAlreadyClosedException)
        assertTrue(exception is HttpException)
    }

    // ── Хелперы ───────────────────────────────────────────────────────────────

    /**
     * Создаёт HttpException для любого HTTP-кода.
     * Retrofit требует Response.error() с ResponseBody — пустое тело достаточно для теста.
     */
    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    private fun assertTrue(message: String, condition: Boolean) =
        org.junit.Assert.assertTrue(message, condition)

    private fun assertTrue(condition: Boolean) =
        org.junit.Assert.assertTrue(condition)

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
