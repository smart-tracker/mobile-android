package com.example.smarttracker.data.repository

import com.example.smarttracker.data.local.IconCacheManager
import com.example.smarttracker.data.local.db.ActivityTypeDao
import com.example.smarttracker.data.local.db.PendingFinishDao
import com.example.smarttracker.data.local.db.PendingFinishEntity
import com.example.smarttracker.data.local.db.toDomain
import com.example.smarttracker.data.local.db.toEntity
import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.data.remote.TrainingApiService
import com.example.smarttracker.data.remote.dto.GpsPointsBatchRequestDto
import com.example.smarttracker.data.remote.dto.TrainingSaveRequestDto
import com.example.smarttracker.data.remote.dto.TrainingStartRequestDto
import com.example.smarttracker.data.remote.dto.toDomain
import com.example.smarttracker.data.remote.dto.toGpsPointDto
import com.example.smarttracker.domain.model.ActiveTrainingConflictException
import com.example.smarttracker.domain.model.ActiveTrainingResult
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.domain.model.METActivity
import com.example.smarttracker.domain.model.NetworkUnavailableException
import com.example.smarttracker.domain.model.SaveTrainingResult
import com.example.smarttracker.domain.model.TrainingAlreadyClosedException
import com.example.smarttracker.domain.model.WorkoutType
import com.example.smarttracker.domain.repository.WorkoutRepository
import retrofit2.HttpException
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация WorkoutRepository: справочные данные + жизненный цикл тренировки.
 *
 * Справочные данные (типы активностей) хранятся в Room (ActivityTypeDao).
 * При каждом вызове [workoutTypesFlow] в фоне запускается сетевое обновление —
 * при успехе Room re-emit'ит обновлённый список через [ActivityTypeDao.observeAll].
 *
 * Логика загрузки иконок:
 * 1. Проверить локальный кэш (filesDir/activity_icons/{id}.png).
 * 2. Если файл есть — передать его в WorkoutType.iconFile (отобразится мгновенно).
 * 3. Если файла нет и бэкенд прислал image_path — запустить загрузку в фоне.
 *    При следующем вызове workoutTypesFlow() файл уже будет закэширован.
 * 4. Пока файла нет — iconFile = null, UI покажет drawable-fallback по iconKey.
 */
@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
    private val trainingApi: TrainingApiService,
    private val iconCache: IconCacheManager,
    private val activityTypeDao: ActivityTypeDao,
    private val pendingFinishDao: PendingFinishDao,
) : WorkoutRepository {

    /**
     * Отдельный scope для фоновых загрузок иконок и сетевых обновлений.
     * SupervisorJob — ошибка одной корутины не отменяет остальные.
     * Scope живёт столько же, сколько синглтон (т.е. всё время работы приложения).
     */
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun workoutTypesFlow(): Flow<List<WorkoutType>> {
        // Запускаем обновление конкурентно: не блокирует первый emit из Room.
        // downloadScope гарантирует, что запрос живёт дольше подписчика Flow.
        downloadScope.launch { refreshFromNetwork() }
        return activityTypeDao.observeAll()
            .map { entities -> entities.map { it.toDomain(iconCache) } }
    }

    /**
     * Загружает виды активности из сети и сохраняет в Room через upsert.
     * После upsertAll Room автоматически re-emit'ит [ActivityTypeDao.observeAll].
     * Ошибки сети перехватываются тихо — кэш уже показан в UI.
     */
    private suspend fun refreshFromNetwork() {
        runCatching {
            val dtos = api.getActivityTypes()
            activityTypeDao.upsertAll(dtos.map { it.toEntity() })
            dtos.forEach { dto ->
                if (dto.imagePath != null && iconCache.getCached(dto.id) == null) {
                    downloadScope.launch { iconCache.download(dto.id, dto.imagePath) }
                }
            }
        }
    }

    override suspend fun startTraining(typeActivId: Int): Result<ActiveTrainingResult> =
        runCatching {
            try {
                trainingApi.startTraining(TrainingStartRequestDto(typeActivId)).toDomain()
            } catch (e: HttpException) {
                // 400 означает что у пользователя уже есть активная тренировка на сервере.
                // Бросаем доменное исключение — ViewModel получит его без зависимости от Retrofit.
                if (e.code() == 400) throw ActiveTrainingConflictException()
                throw e
            }
        }

    override suspend fun getActiveTraining(): Result<String> = runCatching {
        trainingApi.getActiveTraining().activeTrainingId
    }

    override suspend fun saveTraining(
        trainingId: String,
        timeEnd: String,
        totalDistanceMeters: Double?,
        totalKilocalories: Double?,
    ): Result<SaveTrainingResult> = runCatching {
        try {
            trainingApi.saveTraining(
                trainingId,
                TrainingSaveRequestDto(timeEnd, totalDistanceMeters, totalKilocalories),
            ).toDomain()
        } catch (e: IOException) {
            // Сеть недоступна — вызывающий код может поставить операцию в очередь
            throw NetworkUnavailableException(e)
        } catch (e: HttpException) {
            // 4xx — тренировка уже закрыта или не существует; ретрай бессмысленен
            if (e.code() in 400..499) throw TrainingAlreadyClosedException(e.code())
            throw e  // 5xx пробрасываем как есть → воркер сделает retry
        }
    }

    override suspend fun uploadGpsPoints(
        trainingId: String,
        batchId: String,
        points: List<LocationPoint>,
    ): Result<Int> = runCatching {
        trainingApi.uploadGpsPoints(
            trainingId,
            GpsPointsBatchRequestDto(batchId, points.map { it.toGpsPointDto() }),
        ).saved
    }

    override suspend fun getMETActivity(typeActivId: Int): Result<METActivity> =
        runCatching {
            trainingApi.getMETActivity(typeActivId).toDomain()
        }

    override suspend fun savePendingFinish(
        trainingId: String,
        timeEnd: String,
        totalDistanceMeters: Double?,
        totalKilocalories: Double?,
    ) {
        pendingFinishDao.insert(
            PendingFinishEntity(
                trainingId          = trainingId,
                timeEnd             = timeEnd,
                totalDistanceMeters = totalDistanceMeters,
                totalKilocalories   = totalKilocalories,
            )
        )
    }
}
