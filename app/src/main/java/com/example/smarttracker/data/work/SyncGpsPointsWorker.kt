package com.example.smarttracker.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.smarttracker.data.local.db.PendingFinishDao
import com.example.smarttracker.data.location.LocationConfig
import com.example.smarttracker.domain.model.ActiveTrainingConflictException
import com.example.smarttracker.domain.repository.LocationRepository
import com.example.smarttracker.domain.repository.WorkoutRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID

/**
 * WorkManager-воркер для загрузки несинхронизированных GPS-точек на сервер.
 *
 * Запускается как первый шаг цепочки `offline_finish_{trainingId}` перед
 * [SaveTrainingWorker]: важно загрузить GPS-точки до закрытия тренировки,
 * чтобы сервер принял их.
 *
 * **Офлайн-старт:** если тренировка была начата без сети (localUUID), в
 * [PendingFinishDao] хранится [typeActivId]. В этом случае воркер сначала
 * регистрирует тренировку на сервере, переключает GPS-точки на serverUUID
 * (re-key в Room), затем загружает точки. Resolved serverUUID передаётся
 * в [SaveTrainingWorker] через output data ([KEY_RESOLVED_TRAINING_ID]).
 *
 * **Несколько офлайн-тренировок подряд:** если сервер возвращает 400 (конфликт)
 * при попытке регистрации — воркер возвращает [Result.retry]. После того как
 * предыдущая тренировка будет закрыта своим [SaveTrainingWorker], следующий
 * retry успешно зарегистрирует эту тренировку.
 *
 * После [MAX_ATTEMPTS] неудачных попыток возвращает [Result.success], чтобы
 * не блокировать следующий шаг цепочки ([SaveTrainingWorker]) — закрыть
 * тренировку важнее, чем доставить все GPS-точки.
 */
@HiltWorker
class SyncGpsPointsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val locationRepository: LocationRepository,
    private val workoutRepository: WorkoutRepository,
    private val pendingFinishDao: PendingFinishDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val trainingId = inputData.getString(KEY_TRAINING_ID)
            ?: run {
                Log.e(TAG, "Missing trainingId in input data")
                return Result.failure()
            }

        // После MAX_ATTEMPTS разблокируем цепочку: save_training важнее GPS-точек.
        // НО: если тренировка ещё не зарегистрирована на сервере (typeActivId != null),
        // передавать localUUID в SaveTrainingWorker нельзя — сервер вернёт 404 и
        // SaveTrainingWorker удалит pending-запись → данные тренировки потеряны навсегда.
        // В этом случае возвращаем failure(): данные остаются в Room, цепочка завершается
        // без удаления записи (лучше потерять GPS-точки, чем всю тренировку целиком).
        if (runAttemptCount >= MAX_ATTEMPTS) {
            val pending = pendingFinishDao.getById(trainingId)
            if (pending?.typeActivId != null) {
                Log.e(TAG, "Max GPS sync attempts ($MAX_ATTEMPTS) for unregistered offline training $trainingId — failing chain to preserve pending data")
                return Result.failure()
            }
            Log.w(TAG, "Max GPS sync attempts ($MAX_ATTEMPTS) for $trainingId, unblocking chain")
            return Result.success(workDataOf(KEY_RESOLVED_TRAINING_ID to trainingId))
        }

        // Если тренировка начата офлайн (typeActivId != null) — сначала зарегистрировать на сервере.
        val resolvedTrainingId = resolveTrainingId(trainingId) ?: return Result.retry()

        val unsent = locationRepository.getUnsentPoints(resolvedTrainingId)
        if (unsent.isEmpty()) {
            Log.d(TAG, "No unsent GPS points for $resolvedTrainingId")
            return Result.success(workDataOf(KEY_RESOLVED_TRAINING_ID to resolvedTrainingId))
        }

        Log.d(TAG, "Syncing ${unsent.size} unsent GPS points for $resolvedTrainingId (attempt ${runAttemptCount + 1}/$MAX_ATTEMPTS)")

        var hasFailures = false

        // Ретрай батчей с уже назначенным batchId — используем старый ID для идемпотентности
        unsent.filter { it.batchId != null }
            .groupBy { requireNotNull(it.batchId) }
            .forEach { (batchId, points) ->
                workoutRepository.uploadGpsPoints(resolvedTrainingId, batchId, points)
                    .onSuccess {
                        Log.d(TAG, "GPS batch retry success: ${points.size} points, batchId=$batchId")
                        locationRepository.markBatchAsSent(batchId)
                    }
                    .onFailure { e ->
                        Log.w(TAG, "GPS batch retry failed, batchId=$batchId", e)
                        hasFailures = true
                    }
            }

        // Новые точки (batchId == null): назначаем свежий batchId
        unsent.filter { it.batchId == null }
            .chunked(LocationConfig.GPS_BATCH_MAX_SIZE)
            .forEach { chunk ->
                val batchId = UUID.randomUUID().toString()
                locationRepository.assignBatchId(chunk.map { it.id }, batchId)
                workoutRepository.uploadGpsPoints(resolvedTrainingId, batchId, chunk)
                    .onSuccess {
                        Log.d(TAG, "GPS batch uploaded: ${chunk.size} points, batchId=$batchId")
                        locationRepository.markBatchAsSent(batchId)
                    }
                    .onFailure { e ->
                        Log.w(TAG, "GPS batch upload failed, batchId=$batchId", e)
                        hasFailures = true
                    }
            }

        return if (hasFailures) {
            Result.retry()
        } else {
            Result.success(workDataOf(KEY_RESOLVED_TRAINING_ID to resolvedTrainingId))
        }
    }

    /**
     * Если тренировка начата офлайн (typeActivId != null в PendingFinishEntity) —
     * регистрирует её на сервере, переключает GPS-точки на serverUUID и обновляет
     * PendingFinishEntity. Возвращает resolvedTrainingId (serverUUID или исходный id).
     *
     * Возвращает null если регистрация нужна, но не удалась — сигнал для retry.
     */
    private suspend fun resolveTrainingId(trainingId: String): String? {
        val pending = pendingFinishDao.getById(trainingId) ?: return trainingId
        val typeActivId = pending.typeActivId ?: return trainingId

        Log.d(TAG, "Offline-started training $trainingId, registering on server (typeActivId=$typeActivId, timeStart=${pending.timeStart})")

        val result = workoutRepository.startTraining(typeActivId, pending.timeStart)
        val serverUUID = result.getOrElse { e ->
            if (e is ActiveTrainingConflictException) {
                // Предыдущая тренировка ещё активна — ждём пока её закроет свой воркер
                Log.w(TAG, "Conflict while registering offline training $trainingId, will retry")
            } else {
                Log.w(TAG, "Failed to register offline training $trainingId", e)
            }
            return null
        }.activeTrainingId

        // Переключаем GPS-точки: localUUID → serverUUID
        locationRepository.rekeyTrainingId(trainingId, serverUUID)

        // Обновляем PendingFinish: удаляем старую запись, вставляем с serverUUID
        pendingFinishDao.delete(trainingId)
        pendingFinishDao.insert(pending.copy(trainingId = serverUUID, typeActivId = null))

        Log.d(TAG, "Offline training registered: $trainingId → $serverUUID")
        return serverUUID
    }

    companion object {
        /** Ключ для передачи trainingId через WorkManager input data. */
        const val KEY_TRAINING_ID = "training_id"

        /**
         * Ключ resolved trainingId в output data — serverUUID после регистрации офлайн-тренировки.
         * [SaveTrainingWorker] читает его из inputData для вызова saveTraining с правильным ID.
         */
        const val KEY_RESOLVED_TRAINING_ID = "resolved_training_id"

        private const val TAG = "SyncGpsPointsWorker"

        /**
         * После MAX_ATTEMPTS GPS-sync разблокирует цепочку (возвращает success).
         * GPS-точки важны, но закрыть тренировку важнее — не блокируем save_training вечно.
         */
        private const val MAX_ATTEMPTS = 5
    }
}
