package com.example.smarttracker.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smarttracker.data.local.db.PendingFinishDao
import com.example.smarttracker.domain.model.TrainingAlreadyClosedException
import com.example.smarttracker.domain.repository.WorkoutRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager-воркер для доставки офлайн-завершений тренировок на сервер.
 *
 * Запускается автоматически при появлении сети (constraint CONNECTED).
 * Читает все записи из [PendingFinishDao], отправляет каждую через
 * [WorkoutRepository.saveTraining], при успехе удаляет запись из очереди.
 *
 * Обработка ошибок:
 * - [TrainingAlreadyClosedException] (HTTP 4xx) — тренировка уже закрыта
 *   (auto-recovery успел раньше) — запись удаляется, retry не нужен.
 * - NetworkUnavailableException / 5xx — транзиентная ошибка → [Result.retry] с backoff.
 * - Достигнут [MAX_ATTEMPTS] — очередь очищается, [Result.failure] (не блокируем вечно).
 */
@HiltWorker
class SaveTrainingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pendingFinishDao: PendingFinishDao,
    private val workoutRepository: WorkoutRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Лимит попыток: при permanent-ошибке не крутимся вечно.
        // Очищаем очередь — лучше потерять данные, чем блокировать WorkManager навсегда.
        if (runAttemptCount >= MAX_ATTEMPTS) {
            Log.e(TAG, "Max retry attempts ($MAX_ATTEMPTS) reached, clearing pending queue")
            pendingFinishDao.getAll().forEach { pendingFinishDao.delete(it.trainingId) }
            return Result.failure()
        }

        val pending = pendingFinishDao.getAll()
        if (pending.isEmpty()) return Result.success()

        Log.d(TAG, "doWork: ${pending.size} pending finish(es) to sync (attempt ${runAttemptCount + 1}/$MAX_ATTEMPTS)")

        var hasTransientFailures = false
        for (item in pending) {
            workoutRepository.saveTraining(
                trainingId          = item.trainingId,
                timeEnd             = item.timeEnd,
                totalDistanceMeters = item.totalDistanceMeters,
                totalKilocalories   = item.totalKilocalories,
            ).onSuccess {
                Log.d(TAG, "saveTraining success for ${item.trainingId}, removing from queue")
                pendingFinishDao.delete(item.trainingId)
            }.onFailure { e ->
                when (e) {
                    is TrainingAlreadyClosedException -> {
                        // Тренировка уже закрыта (auto-recovery отработал раньше, или не существует).
                        // Повтор бессмысленен — удаляем запись из очереди.
                        Log.w(TAG, "Training ${item.trainingId} already closed (HTTP ${e.httpCode}), removing from queue")
                        pendingFinishDao.delete(item.trainingId)
                    }
                    else -> {
                        // Сеть или 5xx — транзиентная ошибка, повторим через backoff
                        Log.w(TAG, "saveTraining failed for ${item.trainingId}, will retry", e)
                        hasTransientFailures = true
                    }
                }
            }
        }

        return if (hasTransientFailures) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "SaveTrainingWorker"

        /** Уникальное имя work-задачи (используется в цепочке offline_finish_{trainingId}). */
        const val WORK_NAME = "pending_saves_sync"

        /** Максимальное число попыток до принудительной очистки очереди. */
        private const val MAX_ATTEMPTS = 5
    }
}
