package com.example.smarttracker.data.repository

import com.example.smarttracker.data.local.IconCacheManager
import com.example.smarttracker.data.local.db.ActivityTypeDao
import com.example.smarttracker.data.local.db.METActivityDao
import com.example.smarttracker.data.local.db.PendingFinishDao
import com.example.smarttracker.data.local.db.PendingFinishEntity
import com.example.smarttracker.data.local.db.toDomain
import com.example.smarttracker.data.local.db.toEntity
import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.data.remote.TrainingApiService
import com.example.smarttracker.data.remote.dto.GpsPointsBatchRequestDto
import com.example.smarttracker.data.remote.dto.TrainingSaveRequestDto
import com.example.smarttracker.data.remote.dto.TrainingStartRequestDto
import com.example.smarttracker.data.remote.dto.gpsPointsToDomain
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация WorkoutRepository: справочные данные + жизненный цикл тренировки.
 *
 * **Типы активностей** хранятся в Room (ActivityTypeDao).
 * При вызове [workoutTypesFlow] запускается фоновое обновление (stale-while-revalidate).
 * Дебаунс: если обновление уже выполняется, повторный вызов его не дублирует.
 *
 * **Иконки**: скачиваются по URL из бэкенда и кэшируются в filesDir.
 * При смене imagePath на бэкенде файл перезагружается (сравнение через [IconCacheManager.getDownloadedUrl]).
 *
 * **MET-коэффициенты** кэшируются в Room (METActivityDao) с TTL 24 часа.
 * Предзагрузка запускается в [refreshFromNetwork] для каждого типа активности
 * без кэша или с устаревшим кэшем. [getMETActivity] возвращает кэш мгновенно;
 * при отсутствии кэша делает синхронный сетевой запрос.
 */
@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
    private val trainingApi: TrainingApiService,
    private val iconCache: IconCacheManager,
    private val activityTypeDao: ActivityTypeDao,
    private val metActivityDao: METActivityDao,
    private val pendingFinishDao: PendingFinishDao,
) : WorkoutRepository {

    /**
     * Отдельный scope для фоновых загрузок иконок, обновления типов и предзагрузки MET.
     * SupervisorJob — ошибка одной корутины не отменяет остальные.
     * Scope живёт столько же, сколько синглтон (т.е. всё время работы приложения).
     */
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Дебаунс: не допускаем параллельных вызовов refreshFromNetwork при быстрой навигации. */
    private var activeRefreshJob: Job? = null

    /**
     * Эмитит [Unit] при каждом успешном сохранении тренировки на сервере.
     * extraBufferCapacity=1: если подписчик ещё не готов, событие не теряется.
     * Используется [TrainingHistoryViewModel] для автообновления истории.
     */
    private val _trainingCompletedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val trainingCompletedFlow: SharedFlow<Unit> = _trainingCompletedFlow.asSharedFlow()

    override fun workoutTypesFlow(): Flow<List<WorkoutType>> {
        // Запускаем обновление только если предыдущее уже завершилось.
        // downloadScope гарантирует, что запрос живёт дольше подписчика Flow.
        if (activeRefreshJob?.isActive != true) {
            activeRefreshJob = downloadScope.launch { refreshFromNetwork() }
        }
        return activityTypeDao.observeAll()
            .map { entities -> entities.map { it.toDomain(iconCache) } }
    }

    /**
     * Загружает виды активности из сети, сохраняет в Room через upsert.
     * После upsertAll Room автоматически re-emit'ит [ActivityTypeDao.observeAll].
     * Для каждого типа запускает фоновую предзагрузку MET и обновление иконки при смене URL.
     * Ошибки сети перехватываются тихо — кэш уже показан в UI.
     */
    private suspend fun refreshFromNetwork() {
        runCatching {
            val dtos = api.getActivityTypes()
            activityTypeDao.upsertAll(dtos.map { it.toEntity() })
            dtos.forEach { dto ->
                // Предзагрузка MET: фон, только если кэша нет или он старше TTL.
                val metCached = metActivityDao.getWithZones(dto.id)
                val metIsStale = metCached == null ||
                    (System.currentTimeMillis() - metCached.activity.cachedAt) > MET_CACHE_TTL_MS
                if (metIsStale) {
                    downloadScope.launch { fetchAndCacheMET(dto.id) }
                }
                // Иконка: перезагрузить если файла нет или URL изменился на бэкенде.
                if (dto.imagePath != null &&
                    (iconCache.getCached(dto.id) == null ||
                        iconCache.getDownloadedUrl(dto.id) != dto.imagePath)
                ) {
                    downloadScope.launch { iconCache.download(dto.id, dto.imagePath) }
                }
            }
        }
    }

    /**
     * Загружает MET-данные из сети и сохраняет в Room.
     * deleteZones перед upsertZones предотвращает накопление устаревших записей
     * при сокращении количества зон на бэкенде.
     */
    private suspend fun fetchAndCacheMET(typeActivId: Int) {
        runCatching {
            val dto = trainingApi.getMETActivity(typeActivId)
            metActivityDao.upsertActivity(dto.toEntity(typeActivId, System.currentTimeMillis()))
            metActivityDao.deleteZones(typeActivId)
            metActivityDao.upsertZones(dto.zones.map { it.toEntity(typeActivId) })
        }
    }

    override suspend fun startTraining(typeActivId: Int, timeStart: String?): Result<ActiveTrainingResult> =
        runCatching {
            try {
                trainingApi.startTraining(TrainingStartRequestDto(typeActivId, timeStart)).toDomain()
            } catch (e: IOException) {
                // Сеть недоступна. Оборачиваем как в saveTraining — ViewModel покажет
                // "Нет связи" вместо общего "Не удалось зарегистрировать", а SyncGpsPointsWorker
                // сможет отличить офлайн (retry) от постоянной 4xx (fail-fast).
                throw NetworkUnavailableException(e)
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
            val result = trainingApi.saveTraining(
                trainingId,
                TrainingSaveRequestDto(timeEnd, totalDistanceMeters, totalKilocalories),
            ).toDomain()
            // Уведомляем подписчиков об успешном завершении тренировки.
            // Работает и для онлайн-завершения (WorkoutStartViewModel), и для офлайн
            // (SaveTrainingWorker → этот же метод). TrainingHistoryViewModel перезагрузит историю.
            _trainingCompletedFlow.tryEmit(Unit)
            result
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

    /**
     * Возвращает MET-конфигурацию из кэша Room (TTL 24 ч).
     * При устаревшем или отсутствующем кэше — синхронный сетевой запрос.
     * В штатном режиме кэш уже заполнен [refreshFromNetwork], запрос в сеть не нужен.
     */
    override suspend fun getMETActivity(typeActivId: Int): Result<METActivity> {
        val cached = metActivityDao.getWithZones(typeActivId)
        val isStale = cached == null ||
            (System.currentTimeMillis() - cached.activity.cachedAt) > MET_CACHE_TTL_MS
        if (!isStale) return Result.success(cached!!.toDomain())
        return runCatching {
            fetchAndCacheMET(typeActivId)
            val refreshed = metActivityDao.getWithZones(typeActivId)
                ?: throw IllegalStateException(
                    "MET activity for typeActivId=$typeActivId was not cached after refresh"
                )
            refreshed.toDomain()
        }
    }

    companion object {
        /** TTL кэша MET-коэффициентов: 24 часа. */
        private const val MET_CACHE_TTL_MS = 24L * 60 * 60 * 1000
    }

    override suspend fun getTrainingHistory(): Result<List<com.example.smarttracker.domain.model.TrainingHistoryItem>> =
        runCatching {
            trainingApi.getTrainingHistory().map { it.toDomain() }
        }

    override suspend fun getTrainingDetail(trainingId: String): Result<List<com.example.smarttracker.domain.model.LocationPoint>> =
        runCatching {
            trainingApi.getTrainingDetail(trainingId).gpsPointsToDomain()
        }

    override suspend fun savePendingFinish(
        trainingId: String,
        timeEnd: String,
        totalDistanceMeters: Double?,
        totalKilocalories: Double?,
        typeActivId: Int?,
        timeStart: String?,
    ) {
        pendingFinishDao.insert(
            PendingFinishEntity(
                trainingId          = trainingId,
                timeEnd             = timeEnd,
                totalDistanceMeters = totalDistanceMeters,
                totalKilocalories   = totalKilocalories,
                typeActivId         = typeActivId,
                timeStart           = timeStart,
            )
        )
    }
}
