package com.example.smarttracker.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Единственная Room-база данных приложения.
 *
 * **version = 2:** добавлены поля [GpsPointEntity.bearing] (Float?) и [GpsPointEntity.externalId] (String?).
 * Используется [fallbackToDestructiveMigration] — данные тренировок хранятся только на устройстве,
 * а не являются критичными для пользователя (production-миграция будет добавлена в Этапе 5).
 *
 * exportSchema = false — отключает генерацию JSON-схемы.
 * Экземпляр создаётся один раз через Hilt в AuthModule (Singleton).
 */
@Database(
    entities = [GpsPointEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SmartTrackerDatabase : RoomDatabase() {
    abstract fun gpsPointDao(): GpsPointDao
}
