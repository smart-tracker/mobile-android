package com.example.smarttracker.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Единственная Room-база данных приложения.
 *
 * **version = 2:** добавлены поля [GpsPointEntity.bearing] (Float?) и [GpsPointEntity.externalId] (String?).
 * **version = 3:** добавлено поле [GpsPointEntity.calories] (Double?) — расход ккал за интервал (MET-метод).
 * **version = 4:** добавлена таблица [ActivityTypeEntity] — кэш видов активности (stale-while-revalidate).
 * **version = 5:** добавлена таблица [PendingFinishEntity] — очередь офлайн-завершений тренировок.
 * **version = 8:** добавлены таблицы [METActivityEntity] и [MetZoneEntity] — кэш MET-коэффициентов
 *   с TTL 24 часа; предзагружаются в фоне при обновлении списка типов активностей.
 * Используется [fallbackToDestructiveMigration] — данные тренировок хранятся только на устройстве,
 * а не являются критичными для пользователя (production-миграция будет добавлена в Этапе 5).
 *
 * exportSchema = false — отключает генерацию JSON-схемы.
 * Экземпляр создаётся один раз через Hilt в AuthModule (Singleton).
 */
@Database(
    entities = [
        GpsPointEntity::class,
        ActivityTypeEntity::class,
        PendingFinishEntity::class,
        METActivityEntity::class,
        MetZoneEntity::class,
    ],
    version = 8,
    exportSchema = false
)
abstract class SmartTrackerDatabase : RoomDatabase() {
    abstract fun gpsPointDao(): GpsPointDao
    abstract fun activityTypeDao(): ActivityTypeDao
    abstract fun pendingFinishDao(): PendingFinishDao
    abstract fun metActivityDao(): METActivityDao

    companion object {
        /** v5→v6: добавлено поле typeActivId в pending_finishes для офлайн-старта тренировок. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_finishes ADD COLUMN typeActivId INTEGER")
            }
        }

        /**
         * v6→v7: добавлено поле timeStart в pending_finishes.
         * Хранит реальное время начала офлайн-тренировки (ISO 8601 UTC) — передаётся
         * в POST /training/start чтобы бэкенд записал правильный time_start.
         * Без этого поля time_start на сервере = момент синхронизации (после появления сети),
         * что всегда позже реального окончания → time_end < time_start.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_finishes ADD COLUMN timeStart TEXT")
            }
        }

        /**
         * v7→v8: добавлены таблицы met_activities и met_zones для кэширования MET-данных.
         * met_zones имеет внешний ключ на met_activities с CASCADE DELETE.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE met_activities (
                        typeActivId INTEGER PRIMARY KEY NOT NULL,
                        baseMet REAL NOT NULL,
                        usesSpeedZones INTEGER NOT NULL,
                        cachedAt INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE met_zones (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        typeActivId INTEGER NOT NULL,
                        speedMin REAL NOT NULL,
                        speedMax REAL NOT NULL,
                        metValue REAL NOT NULL,
                        FOREIGN KEY(typeActivId) REFERENCES met_activities(typeActivId) ON DELETE CASCADE
                    )"""
                )
                db.execSQL(
                    "CREATE INDEX index_met_zones_typeActivId ON met_zones (typeActivId)"
                )
            }
        }
    }
}
