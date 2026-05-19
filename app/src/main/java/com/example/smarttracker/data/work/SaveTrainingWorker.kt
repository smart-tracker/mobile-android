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
        // SyncGpsPointsWorker передаёт resolvedId если офлайн-тренировка была перерегистрирована
        // (localUUID → serverUUID). Используем его для поиска в PendingFinish и вызова saveTraining.
        val resolvedId = inputData.getString(SyncGpsPointsWorker.KEY_RESOLVED_TRAINING_ID)
            ?: trainingId

        // Лимит попыток: при permanent-ошибке не крутимся вечно.
        // Удаляем запись — лучше потерять данные, чем блокировать WorkManager навсегда.
        if (runAttemptCount >= MAX_ATTEMPTS) {
            Log.e(TAG, "Max retry attempts ($MAX_ATTEMPTS) reached${resolvedId?.let { " for trainingId=$it" } ?: " for all pending records"}")
            if (resolvedId != null) {
                pendingFinishDao.delete(resolvedId)
            } else {
                pendingFinishDao.getAll().forEach { pendingFinishDao.delete(it.trainingId) }
            }
            return Result.failure()
        }

        // Если передан конкретный resolvedId — обрабатываем только его.
        // Это исключает дублирование запросов при параллельном запуске
        // нескольких воркеров (по одному на каждую офлайн-тренировку).
        //
        // Fallback на getAll(): SyncGpsPointsWorker мог вернуть стale localUUID как
        // resolvedId если его retry-попытка сработала после успешного re-key (localUUID → serverUUID).
        // В этом случае getById(localUUID) == null, но serverUUID-запись существует.
        // getAll() подхватит её и закроет тренировку. Безопасно: другие SaveTrainingWorker-ы
        // для дополнительных записей тоже вернут success() с пустым pending — дублей нет.
        val pending = if (resolvedId != null) {
            val byId = pendingFinishDao.getById(resolvedId)
            if (byId != null) {
                listOf(byId)
            } else {
                val all = pendingFinishDao.getAll()
                if (all.isNotEmpty()) {
                    Log.w(TAG, "No pending entry for resolvedId=$resolvedId (possible stale localUUID after re-key), fallback to ${all.size} entries")
                }
                all
            }
        } else {
            pendingFinishDao.getAll()
        }

        if (pending.isEmpty()) return Result.success()

        Log.d(TAG, "doWork: ${pending.size} pending finish(es) to sync (attempt ${runAttemptCount + 1}/$MAX_ATTEMPTS)")

        var hasTransientFailures = false
        for (item in pending) {
            // Фолбэк на 0.0 для null-полей: бэкенд валит 500 при отсутствии
            // total_distance_meters / total_kilocalories в теле (Optional на
            // схеме, но не обрабатываемое на сервере). 0.0 валиден семантически.
            // Защищает от старых записей в Room, созданных до фикса в
            // WorkoutStartViewModel.onFinishClick (тогда поля могли быть null).
            workoutRepository.saveTraining(
                trainingId          = item.trainingId,
                timeEnd             = item.timeEnd,
                totalDistanceMeters = item.totalDistanceMeters ?: 0.0,
                totalKilocalories   = item.totalKilocalories ?: 0.0,
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
