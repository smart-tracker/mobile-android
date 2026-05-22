package com.example.smarttracker.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.smarttracker.data.local.db.PendingFinishDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Планировщик offline-finish цепочки `SyncGpsPointsWorker` → `SaveTrainingWorker`.
 *
 * Цепочка доставляет на сервер тренировку, завершённую без сети: сначала грузит
 * GPS-точки, затем закрывает тренировку. Уникальное имя `offline_finish_{id}`.
 *
 * Вынесен в отдельный singleton, потому что enqueue нужен из двух мест:
 *  - WorkoutStartViewModel — при офлайн-финише тренировки;
 *  - AppViewModel — реконсиляция при старте приложения (см. [reconcilePending]).
 */
@Singleton
class OfflineFinishScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val pendingFinishDao: PendingFinishDao,
) {

    private val workManager = WorkManager.getInstance(context)

    /**
     * Ставит цепочку GPS-sync → save_training в WorkManager.
     * GPS-точки загружаются первыми, чтобы сервер принял их до закрытия тренировки.
     * Уникальное имя по trainingId, [ExistingWorkPolicy.KEEP] — не заменяем уже
     * запущенную цепочку (повторный вызов для живой цепочки — no-op).
     */
    fun enqueue(trainingId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val gpsWork = OneTimeWorkRequestBuilder<SyncGpsPointsWorker>()
            .setInputData(workDataOf(SyncGpsPointsWorker.KEY_TRAINING_ID to trainingId))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        val saveWork = OneTimeWorkRequestBuilder<SaveTrainingWorker>()
            .setInputData(workDataOf(SaveTrainingWorker.KEY_TRAINING_ID to trainingId))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager
            .beginUniqueWork("offline_finish_$trainingId", ExistingWorkPolicy.KEEP, gpsWork)
            .then(saveWork)
            .enqueue()
    }

    /**
     * Перепланирует offline-finish цепочки для всех ещё не синхронизированных тренировок.
     *
     * Зачем: цепочка могла «умереть» (`Result.failure` после `MAX_ATTEMPTS` в
     * [SyncGpsPointsWorker]), пока слот активной тренировки на сервере был занят
     * другой — например, длинной живой тренировкой. После её завершения мёртвую
     * цепочку никто не перезапускал → тренировка оставалась в `PendingFinishDao`
     * навсегда. Реконсиляция при старте приложения чинит это.
     *
     * [ExistingWorkPolicy.KEEP] в [enqueue] не дублирует ещё живые цепочки.
     */
    suspend fun reconcilePending() {
        pendingFinishDao.getAll().forEach { enqueue(it.trainingId) }
    }
}
