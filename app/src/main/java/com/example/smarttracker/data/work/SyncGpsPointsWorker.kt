package com.example.smarttracker.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smarttracker.data.location.LocationConfig
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
 * Логика полностью повторяет [LocationTrackingService.syncUnsentPoints], но
 * работает через инжектируемые репозитории, а не как private-метод сервиса.
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
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val trainingId = inputData.getString(KEY_TRAINING_ID)
            ?: run {
                Log.e(TAG, "Missing trainingId in input data")
                return Result.failure()
            }

        // После MAX_ATTEMPTS разблокируем цепочку: save_training важнее GPS-точек.
        if (runAttemptCount >= MAX_ATTEMPTS) {
            Log.w(TAG, "Max GPS sync attempts ($MAX_ATTEMPTS) for $trainingId, unblocking chain")
            return Result.success()
        }

        val unsent = locationRepository.getUnsentPoints(trainingId)
        if (unsent.isEmpty()) {
            Log.d(TAG, "No unsent GPS points for $trainingId")
            return Result.success()
        }

        Log.d(TAG, "Syncing ${unsent.size} unsent GPS points for $trainingId (attempt ${runAttemptCount + 1}/$MAX_ATTEMPTS)")

        var hasFailures = false

        // Ретрай батчей с уже назначенным batchId — используем старый ID для идемпотентности
        unsent.filter { it.batchId != null }
            .groupBy { requireNotNull(it.batchId) }
            .forEach { (batchId, points) ->
                workoutRepository.uploadGpsPoints(trainingId, batchId, points)
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
                workoutRepository.uploadGpsPoints(trainingId, batchId, chunk)
                    .onSuccess {
                        Log.d(TAG, "GPS batch uploaded: ${chunk.size} points, batchId=$batchId")
                        locationRepository.markBatchAsSent(batchId)
                    }
                    .onFailure { e ->
                        Log.w(TAG, "GPS batch upload failed, batchId=$batchId", e)
                        hasFailures = true
                    }
            }

        return if (hasFailures) Result.retry() else Result.success()
    }

    companion object {
        /** Ключ для передачи trainingId через WorkManager input data. */
        const val KEY_TRAINING_ID = "training_id"

        private const val TAG = "SyncGpsPointsWorker"

        /**
         * После MAX_ATTEMPTS GPS-sync разблокирует цепочку (возвращает success).
         * GPS-точки важны, но закрыть тренировку важнее — не блокируем save_training вечно.
         */
        private const val MAX_ATTEMPTS = 5
    }
}
