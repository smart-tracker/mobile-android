package com.example.smarttracker.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room-сущность для кэширования видов активности.
 *
 * Таблица заполняется из GET /training/types_activity и сохраняется между запусками.
 * При первом запуске содержит три захардкоженных дефолта (Бег, Велосипед, Ходьба),
 * вставленных через RoomDatabase.Callback.onCreate в AuthModule.
 */
@Entity(tableName = "activity_types")
data class ActivityTypeEntity(
    @PrimaryKey val id: Int,
    val name: String,
    /** URL иконки от бэкенда (image_path). null для дефолтных типов и типов без иконки. */
    val imagePath: String?,
)
