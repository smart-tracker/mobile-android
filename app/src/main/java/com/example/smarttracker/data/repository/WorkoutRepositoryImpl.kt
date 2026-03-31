package com.example.smarttracker.data.repository

import com.example.smarttracker.data.local.IconCacheManager
import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.data.remote.dto.toIconKey
import com.example.smarttracker.domain.model.WorkoutType
import com.example.smarttracker.domain.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реальная реализация WorkoutRepository через GET /training/types_activity.
 * Активирована в AuthModule вместо MockWorkoutRepository.
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
}
