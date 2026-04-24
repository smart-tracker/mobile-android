package com.example.smarttracker.data.work

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.smarttracker.data.local.db.PendingFinishDao
import com.example.smarttracker.data.local.db.PendingFinishEntity
import com.example.smarttracker.domain.model.NetworkUnavailableException
import com.example.smarttracker.domain.model.SaveTrainingResult
import com.example.smarttracker.domain.model.TrainingAlreadyClosedException
import com.example.smarttracker.domain.repository.WorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit-тесты SaveTrainingWorker.
 *
 * Покрывает всю логику ветвления в doWork():
 * - Пустая очередь → success без обращения к сети.
 * - Успех → запись удалена из очереди.
 * - TrainingAlreadyClosedException (4xx) → запись удалена, без retry.
 * - Сетевая/транзиентная ошибка → retry, запись остаётся.
 * - MAX_ATTEMPTS исчерпаны → очередь очищена, failure.
 *
 * Используется Robolectric для получения настоящего Android-контекста,
 * необходимого CoroutineWorker. Hilt не задействован — зависимости
 * инжектируются вручную через кастомный WorkerFactory.
 */
@RunWith(RobolectricTestRunner::class)
// application = Application::class: подменяем SmartTrackerApp голым Application, чтобы
// не вызывался SmartTrackerApp.onCreate() → MapLibre.getInstance() → нативные .so-библиотеки,
// которых нет в JVM-среде. Воркерам Application context нужен только как контейнер.
@Config(sdk = [28], application = Application::class)
class SaveTrainingWorkerTest {

    private lateinit var context: Context
    private lateinit var pendingFinishDao: PendingFinishDao
    private lateinit var workoutRepository: WorkoutRepository

    @Before
    fun setUp() {
        context          = ApplicationProvider.getApplicationContext()
        pendingFinishDao = mock()
        workoutRepository = mock()
    }

    // ── Вспомогательный метод ─────────────────────────────────────────────────

    /**
     * Строит воркер с нужным числом попыток.
     * WorkerFactory вручную подставляет моки вместо Hilt-инжекции.
     */
    private fun buildWorker(runAttemptCount: Int = 0): SaveTrainingWorker =
        TestListenableWorkerBuilder<SaveTrainingWorker>(context)
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = SaveTrainingWorker(
                    appContext, workerParameters, pendingFinishDao, workoutRepository
                )
            })
            .build()

    // ── Тесты ─────────────────────────────────────────────────────────────────

    @Test
    fun `doWork - пустая очередь возвращает success без вызова saveTraining`() = runTest {
        whenever(pendingFinishDao.getAll()).thenReturn(emptyList())

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(workoutRepository, never()).saveTraining(any(), any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `doWork - успешный ответ удаляет запись из очереди`() = runTest {
        val item = pendingItem("uuid-1")
        whenever(pendingFinishDao.getAll()).thenReturn(listOf(item))
        whenever(workoutRepository.saveTraining(any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(SaveTrainingResult("uuid-1", "OK")))

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // Запись должна быть удалена из очереди после успешной доставки
        verify(pendingFinishDao).delete("uuid-1")
    }

    /**
     * TrainingAlreadyClosedException: тренировка уже закрыта (auto-recovery успел раньше).
     * Retry бессмысленен — удаляем запись и возвращаем success.
     */
    @Test
    fun `doWork - TrainingAlreadyClosedException удаляет запись без retry`() = runTest {
        val item = pendingItem("uuid-1")
        whenever(pendingFinishDao.getAll()).thenReturn(listOf(item))
        whenever(workoutRepository.saveTraining(any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.failure(TrainingAlreadyClosedException(404)))

        val result = buildWorker().doWork()

        // Не ретраим — тренировка уже закрыта на сервере
        assertEquals(ListenableWorker.Result.success(), result)
        verify(pendingFinishDao).delete("uuid-1")
    }

    /**
     * Сетевая ошибка — транзиентная, повторим при следующем подключении.
     * Запись должна остаться в очереди.
     */
    @Test
    fun `doWork - NetworkUnavailableException возвращает retry и не удаляет запись`() = runTest {
        val item = pendingItem("uuid-1")
        whenever(pendingFinishDao.getAll()).thenReturn(listOf(item))
        whenever(workoutRepository.saveTraining(any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.failure(NetworkUnavailableException()))

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        verify(pendingFinishDao, never()).delete(any())
    }

    /**
     * Смешанная очередь: одна запись успешна, другая уже закрыта.
     * Обе должны быть удалены, результат — success.
     */
    @Test
    fun `doWork - одна запись успешна, другая уже закрыта - обе удалены`() = runTest {
        val item1 = pendingItem("uuid-1")
        val item2 = pendingItem("uuid-2")
        whenever(pendingFinishDao.getAll()).thenReturn(listOf(item1, item2))
        // eq() обязателен при смешивании с другими матчерами — Mockito требует
        // единообразия: либо все литералы, либо все матчеры.
        whenever(workoutRepository.saveTraining(eq("uuid-1"), any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(SaveTrainingResult("uuid-1", "OK")))
        whenever(workoutRepository.saveTraining(eq("uuid-2"), any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.failure(TrainingAlreadyClosedException(400)))

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(pendingFinishDao).delete("uuid-1")
        verify(pendingFinishDao).delete("uuid-2")
    }

    /**
     * MAX_ATTEMPTS (5) исчерпаны: не блокируем WorkManager вечно.
     * Очередь очищается, возвращается failure.
     * saveTraining не должен вызываться — сразу удаляем.
     */
    @Test
    fun `doWork - при MAX_ATTEMPTS очередь очищается и возвращается failure`() = runTest {
        val items = listOf(pendingItem("uuid-1"), pendingItem("uuid-2"))
        whenever(pendingFinishDao.getAll()).thenReturn(items)

        val result = buildWorker(runAttemptCount = 5).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        // Очередь должна быть очищена
        verify(pendingFinishDao).delete("uuid-1")
        verify(pendingFinishDao).delete("uuid-2")
        // saveTraining не вызывается при исчерпании попыток
        verify(workoutRepository, never()).saveTraining(any(), any(), anyOrNull(), anyOrNull())
    }

    // ── Хелперы ───────────────────────────────────────────────────────────────

    private fun pendingItem(trainingId: String) = PendingFinishEntity(
        trainingId          = trainingId,
        timeEnd             = "2026-04-24T10:00:00Z",
        totalDistanceMeters = 1500.0,
        totalKilocalories   = 80.0,
    )
}
