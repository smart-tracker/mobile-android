package com.example.smarttracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * DAO для таблицы activity_types.
 *
 * [observeAll] — реактивный Flow: Room автоматически эмитит новый список после каждого [upsertAll].
 * [upsertAll] — вставка или замена строк при обновлении из сети.
 */
@Dao
interface ActivityTypeDao {

    @Query("SELECT * FROM activity_types ORDER BY id ASC")
    fun observeAll(): Flow<List<ActivityTypeEntity>>

    @Upsert
    suspend fun upsertAll(types: List<ActivityTypeEntity>)
}
