package com.example.smarttracker.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Единственная Room-база данных приложения.
 *
 * version = 1 — начальная схема. При изменении полей GpsPointEntity нужно
 * увеличить version и добавить Migration, иначе Room выбросит исключение при запуске.
 * exportSchema = false — отключает генерацию JSON-схемы (не нужна в production без CI-проверок).
 *
 * Экземпляр создаётся один раз через Hilt в AuthModule (Singleton).
 * Доступ к DAO — через gpsPointDao().
 */
@Database(
    entities = [GpsPointEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SmartTrackerDatabase : RoomDatabase() {
    abstract fun gpsPointDao(): GpsPointDao
}
