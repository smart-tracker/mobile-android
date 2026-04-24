package com.example.smarttracker.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Единственная Room-база данных приложения.
 *
 * **version = 2:** добавлены поля [GpsPointEntity.bearing] (Float?) и [GpsPointEntity.externalId] (String?).
 * **version = 3:** добавлено поле [GpsPointEntity.calories] (Double?) — расход ккал за интервал (MET-метод).
 * **version = 4:** добавлена таблица [ActivityTypeEntity] — кэш видов активности (stale-while-revalidate).
 * **version = 5:** добавлена таблица [PendingFinishEntity] — очередь офлайн-завершений тренировок.
 * Используется [fallbackToDestructiveMigration] — данные тренировок хранятся только на устройстве,
 * а не являются критичными для пользователя (production-миграция будет добавлена в Этапе 5).
 *
 * exportSchema = false — отключает генерацию JSON-схемы.
 * Экземпляр создаётся один раз через Hilt в AuthModule (Singleton).
 */
@Database(
    entities = [GpsPointEntity::class, ActivityTypeEntity::class, PendingFinishEntity::class],
    version = 5,
    exportSchema = false
)
abstract class SmartTrackerDatabase : RoomDatabase() {
    abstract fun gpsPointDao(): GpsPointDao
    abstract fun activityTypeDao(): ActivityTypeDao
    abstract fun pendingFinishDao(): PendingFinishDao
}
