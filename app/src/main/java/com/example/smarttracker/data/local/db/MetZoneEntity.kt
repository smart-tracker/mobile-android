package com.example.smarttracker.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room-сущность одной зоны скоростей из MET-таблицы.
 *
 * Связана с [METActivityEntity] через [typeActivId].
 * CASCADE DELETE: при удалении активности зоны удаляются автоматически.
 *
 * @param speedMax хранится как [Double.MAX_VALUE] вместо [Double.POSITIVE_INFINITY],
 *   т.к. SQLite не гарантирует корректное хранение IEEE 754 ±∞ через REAL-тип.
 *   Обратное преобразование MAX_VALUE → POSITIVE_INFINITY выполняется в [METMapper].
 */
@Entity(
    tableName = "met_zones",
    foreignKeys = [ForeignKey(
        entity = METActivityEntity::class,
        parentColumns = ["typeActivId"],
        childColumns = ["typeActivId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("typeActivId")],
)
data class MetZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val typeActivId: Int,
    val speedMin: Double,
    val speedMax: Double,
    val metValue: Double,
)
