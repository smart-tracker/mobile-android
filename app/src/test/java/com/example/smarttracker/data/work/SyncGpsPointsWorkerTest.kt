package com.example.smarttracker.data.work

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.domain.model.NetworkUnavailableException
import com.example.smarttracker.domain.repository.LocationRepository
import com.example.smarttracker.domain.repository.WorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit-тесты SyncGpsPointsWorker.
 *
 * Покрывает логику doWork():
 * - Нет несинхронизированных точек → success, сеть не трогается.
 * - Новые точки (batchId == null) → назначается свежий batchId, затем загрузка.
 * - Точки с batchId → ретрай с тем же batchId (идемпотентность).
 * - Ошибка загрузки → retry, markBatchAsSent не вызывается.
 * - MAX_ATTEMPTS → success (разблокируем цепочку, не блокируем SaveTrainingWorker).
 * - Нет trainingId в inputData → failure.
 *
 * Те же технические соглашения, что и в SaveTrainingWorkerTest:
 * Robolectric + ручная инжекция через WorkerFactory.
 */
@RunWith(RobolectricTestRunner::class)
// application = Application::class: та же причина, что и в SaveTrainingWorkerTest —
// предотвращаем загрузку MapLibre нативных .so при инициализации SmartTrackerApp.
@Config(sdk = [28], application = Application::class)
class SyncGpsPointsWorkerTest {

    private lateinit var context: Context
    private lateinit var locationRepository: LocationRepository
    private lateinit var workoutRepository: WorkoutRepository

    @Before
    fun setUp() {
        context              = ApplicationProvider.getApplicationContext()
        locationRepository   = mock()
        workoutRepository    = mock()
    }

    // ── Вспомогательный метод ─────────────────────────────────────────────────

    private fun buildWorker(
        trainingId: String = TRAINING_ID,
        runAttemptCount: Int = 0,
    ): SyncGpsPointsWorker =
        TestListenableWorkerBuilder<SyncGpsPointsWorker>(context)
            .setRunAttemptCount(runAttemptCount)
            .setInputData(workDataOf(SyncGpsPointsWorker.KEY_TRAINING_ID to trainingId))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = SyncGpsPointsWorker(
                    appContext, workerParameters, locationRepository, workoutRepository
                )
            })
            .build()

    // ── Тесты ─────────────────────────────────────────────────────────────────

    @Test
    fun `doWork - нет несинхронизированных точек возвращает success`() = runTest {
        whenever(locationRepository.getUnsentPoints(any())).thenReturn(emptyList())

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(workoutRepository, never()).uploadGpsPoints(any(), any(), any())
    }

    /**
     * Точки без batchId — первый раз попадают в Worker.
     * Воркер должен назначить новый batchId через assignBatchId()
     * и затем загрузить батч на сервер.
     */
    @Test
    fun `doWork - новые точки без batchId назначают batchId и загружают`() = runTest {
        val points = makePoints(3, batchId = null)
        whenever(locationRepository.getUnsentPoints(any())).thenReturn(points)
        whenever(workoutRepository.uploadGpsPoints(any(), any(), any()))
            .thenReturn(Result.success(3))

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // Должен назначить batchId перед загрузкой
        verify(locationRepository).assignBatchId(any(), any())
        // После успешной загрузки пометить батч отправленным
        verify(locationRepository).markBatchAsSent(any())
    }

    /**
     * Точки с уже назначенным batchId — ретрай после предыдущей неудачи.
     * Воркер должен использовать тот же batchId (идемпотентность на сервере).
     * assignBatchId() не должен вызываться повторно.
     */
    @Test
    fun `doWork - точки с batchId используют тот же batchId при ретрае`() = runTest {
        val existingBatchId = "existing-batch-uuid"
        val points = makePoints(2, batchId = existingBatchId)
        whenever(locationRepository.getUnsentPoints(any())).thenReturn(points)
        whenever(workoutRepository.uploadGpsPoints(any(), any(), any()))
            .thenReturn(Result.success(2))

        buildWorker().doWork()

        // Существующий batchId используется без переназначения
        verify(workoutRepository).uploadGpsPoints(any(), eq(existingBatchId), any())
        verify(locationRepository, never()).assignBatchId(any(), any())
        verify(locationRepository).markBatchAsSent(existingBatchId)
    }

    /**
     * Ошибка загрузки → retry, markBatchAsSent не должен вызываться.
     * Точки остаются несинхронизированными для следующей попытки.
     */
    @Test
    fun `doWork - ошибка загрузки возвращает retry и не помечает батч отправленным`() = runTest {
        val points = makePoints(2, batchId = null)
        whenever(locationRepository.getUnsentPoints(any())).thenReturn(points)
        whenever(workoutRepository.uploadGpsPoints(any(), any(), any()))
            .thenReturn(Result.failure(NetworkUnavailableException()))

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        verify(locationRepository, never()).markBatchAsSent(any())
    }

    /**
     * MAX_ATTEMPTS исчерпаны → success (НЕ failure!).
     * GPS-точки важны, но не блокируем SaveTrainingWorker — закрыть тренировку важнее.
     * getUnsentPoints не должен вызываться — сразу возвращаем success.
     */
    @Test
    fun `doWork - MAX_ATTEMPTS достигнут возвращает success чтобы разблокировать цепочку`() = runTest {
        val result = buildWorker(runAttemptCount = 5).doWork()

        // success (не failure) — чтобы SaveTrainingWorker мог запуститься
        assertEquals(ListenableWorker.Result.success(), result)
        verify(locationRepository, never()).getUnsentPoints(any())
    }

    /**
     * trainingId отсутствует в inputData — ошибка конфигурации.
     * Воркер должен вернуть failure, не пытаясь обращаться к репозиториям.
     */
    @Test
    fun `doWork - отсутствует trainingId в inputData возвращает failure`() = runTest {
        // Строим воркер без KEY_TRAINING_ID в inputData
        val worker = TestListenableWorkerBuilder<SyncGpsPointsWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = SyncGpsPointsWorker(
                    appContext, workerParameters, locationRepository, workoutRepository
                )
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        verify(locationRepository, never()).getUnsentPoints(any())
    }

    // ── Хелперы ───────────────────────────────────────────────────────────────

    private fun makePoints(count: Int, batchId: String?): List<LocationPoint> =
        (1..count).map { i ->
            LocationPoint(
                id           = i.toLong(),
                trainingId   = TRAINING_ID,
                timestampUtc = i.toLong() * 1_000L,
                elapsedNanos = i.toLong() * 1_000_000_000L,
                latitude     = 55.7558 + i * 0.0001,
                longitude    = 37.6173 + i * 0.0001,
                altitude     = null,
                speed        = null,
                accuracy     = null,
                batchId      = batchId,
            )
        }

    companion object {
        private const val TRAINING_ID = "test-training-uuid"
    }
}
