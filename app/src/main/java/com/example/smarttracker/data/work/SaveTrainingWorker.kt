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
 * WorkManager-воркер для доставки офлайн-завершения одной тренировки на сервер.
 *
 * Запускается автоматически при появлении сети (constraint CONNECTED).
 * Ожидает [KEY_TRAINING_ID] в inputData — обрабатывает только соответствующую
 * запись из [PendingFinishDao], исключая гонку при параллельном запуске нескольких
 * воркеров (по одному на каждую офлайн-тренировку в цепочке offline_finish_{id}).
 *
 * Если [KEY_TRAINING_ID] не передан — fallback: читает все записи через getAll().
 *
 * Обработка ошибок:
 * - [TrainingAlreadyClosedException] (HTTP 4xx) — тренировка уже закрыта
 *   (auto-recovery успел раньше) — запись удаляется, retry не нужен.
 * - NetworkUnavailableException / 5xx — транзиентная ошибка → [Result.retry] с backoff.
 * - Достигнут [MAX_ATTEMPTS] — запись удаляется, [Result.failure] (не блокируем вечно).
 */
@HiltWorker
class SaveTrainingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pendingFinishDao: PendingFinishDao,
    private val workoutRepository: WorkoutRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val trainingId = inputData.getString(KEY_TRAINING_ID)

        // Лимит попыток: при permanent-ошибке не крутимся вечно.
        // Удаляем запись — лучше потерять данные, чем блокировать WorkManager навсегда.
        if (runAttemptCount >= MAX_ATTEMPTS) {
            Log.e(TAG, "Max retry attempts ($MAX_ATTEMPTS) reached${trainingId?.let { " for trainingId=$it" } ?: " for all pending records"}")
            if (trainingId != null) {
                pendingFinishDao.delete(trainingId)
            } else {
                pendingFinishDao.getAll().forEach { pendingFinishDao.delete(it.trainingId) }
            }
            return Result.failure()
        }

        // Если передан конкретный trainingId — обрабатываем только его.
        // Это исключает дублирование запросов при параллельном запуске
        // нескольких воркеров (по одному на каждую офлайн-тренировку).
        val pending = if (trainingId != null) {
            listOfNotNull(pendingFinishDao.getById(trainingId))
        } else {
            pendingFinishDao.getAll()
        }

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

        /** Ключ для передачи trainingId через inputData. Каждый воркер обрабатывает только свою запись. */
        const val KEY_TRAINING_ID = "key_training_id"

        /** Уникальное имя work-задачи (используется в цепочке offline_finish_{trainingId}). */
        const val WORK_NAME = "pending_saves_sync"

        /** Максимальное число попыток до принудительного удаления записи. */
        private const val MAX_ATTEMPTS = 5
    }
}
