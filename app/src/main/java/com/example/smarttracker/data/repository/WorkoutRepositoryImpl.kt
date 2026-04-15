package com.example.smarttracker.data.repository

import com.example.smarttracker.data.local.IconCacheManager
import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.data.remote.TrainingApiService
import com.example.smarttracker.data.remote.dto.GpsPointsBatchRequestDto
import com.example.smarttracker.data.remote.dto.TrainingSaveRequestDto
import com.example.smarttracker.data.remote.dto.TrainingStartRequestDto
import com.example.smarttracker.data.remote.dto.toDomain
import com.example.smarttracker.data.remote.dto.toGpsPointDto
import com.example.smarttracker.data.remote.dto.toIconKey
import com.example.smarttracker.domain.model.ActiveTrainingResult
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.domain.model.METActivity
import com.example.smarttracker.domain.model.SaveTrainingResult
import com.example.smarttracker.domain.model.WorkoutType
import com.example.smarttracker.domain.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация WorkoutRepository: справочные данные + жизненный цикл тренировки.
 *
 * Справочные данные (типы активностей) загружаются через AuthApiService,
 * операции тренировки (старт, GPS-загрузка, завершение) — через TrainingApiService.
 *
 * Логика загрузки иконок:
 * 1. Проверить локальный кэш (filesDir/activity_icons/{id}.png).
 * 2. Если файл есть — передать его в WorkoutType.iconFile (отобразится мгновенно).
 * 3. Если файла нет и бэкенд прислал image_path — запустить загрузку в фоне.
 *    При следующем вызове getWorkoutTypes() файл уже будет закэширован.
 * 4. Пока файла нет — iconFile = null, UI покажет drawable-fallback по iconKey.
 */
@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
    private val trainingApi: TrainingApiService,
    private val iconCache: IconCacheManager,
) : WorkoutRepository {

    /**
     * Отдельный scope для фоновых загрузок иконок.
     * SupervisorJob — ошибка одной загрузки не отменяет остальные.
     * Scope живёт столько же, сколько синглтон (т.е. всё время работы приложения).
     */
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun getWorkoutTypes(): Result<List<WorkoutType>> =
        runCatching {
            api.getActivityTypes().map { dto ->
                val cachedFile = iconCache.getCached(dto.id)

                // Если image_path появился на бэкенде, но файла ещё нет — скачать в фоне.
                // При следующем открытии экрана иконка уже будет в кэше.
                if (dto.imagePath != null && cachedFile == null) {
                    downloadScope.launch {
                        iconCache.download(dto.id, dto.imagePath)
                    }
                }

                WorkoutType(
                    id       = dto.id,
                    name     = dto.name,
                    iconKey  = dto.toIconKey(),
                    iconFile = cachedFile,
                    imageUrl = dto.imagePath,
                )
            }
        }

    override suspend fun startTraining(typeActivId: Int): Result<ActiveTrainingResult> =
        runCatching {
            trainingApi.startTraining(TrainingStartRequestDto(typeActivId)).toDomain()
        }

    override suspend fun saveTraining(
        trainingId: String,
        timeEnd: String,
        totalDistanceMeters: Double?,
        totalKilocalories: Double?,
    ): Result<SaveTrainingResult> = runCatching {
        trainingApi.saveTraining(
            trainingId,
            TrainingSaveRequestDto(timeEnd, totalDistanceMeters, totalKilocalories),
        ).toDomain()
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
}
