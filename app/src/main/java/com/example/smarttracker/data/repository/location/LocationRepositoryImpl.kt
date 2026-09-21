package com.example.smarttracker.data.repository.location

import android.content.Context
import com.example.smarttracker.data.local.db.GpsPointDao
import com.example.smarttracker.data.local.db.toDomain
import com.example.smarttracker.data.local.db.toEntity
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.domain.repository.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Реализация LocationRepository через Room DAO.
 *
 * Все операции делегируются GpsPointDao. Преобразование между domain-моделью
 * (LocationPoint) и Room-сущностью (GpsPointEntity) выполняется через mapper-функции
 * toEntity() / toDomain(), определённые в GpsPointEntity.kt.
 *
 * Внедрение через конструктор — стандартный паттерн для Hilt + @Binds.
 *
 * [Context] нужен для durable-хранилища последней локации (SharedPreferences) —
 * оно переживает удаление точек Room при финише, чтобы карта центрировалась
 * до GPS-фикса даже после завершённой тренировки.
 */
class LocationRepositoryImpl @Inject constructor(
    private val dao: GpsPointDao,
    @ApplicationContext context: Context,
) : LocationRepository {

    private val lastLocationPrefs =
        context.getSharedPreferences("smarttracker_last_location", Context.MODE_PRIVATE)

    override suspend fun savePoint(point: LocationPoint) {
        dao.insert(point.toEntity())
    }

    override suspend fun savePoints(points: List<LocationPoint>) {
        dao.insertAll(points.map { it.toEntity() })
    }

    override suspend fun getPointsForTraining(trainingId: String): List<LocationPoint> =
        dao.getPointsForTraining(trainingId).map { it.toDomain() }

    override suspend fun getUnsentPoints(trainingId: String): List<LocationPoint> =
        dao.getUnsentPoints(trainingId).map { it.toDomain() }

    override suspend fun assignBatchId(pointIds: List<Long>, batchId: String) {
        dao.assignBatchId(pointIds, batchId)
    }

    override suspend fun markBatchAsSent(batchId: String) {
        dao.markBatchAsSent(batchId)
    }

    override fun observePointsForTraining(trainingId: String): Flow<List<LocationPoint>> =
        dao.observePointsForTraining(trainingId).map { list -> list.map { it.toDomain() } }

    override suspend fun getLastKnownPoint(): LocationPoint? =
        dao.getLastPoint(excludedTrainingId = null)?.toDomain()
            ?: readPersistedLastLocation()

    override suspend fun saveLastKnownLocation(latitude: Double, longitude: Double) {
        // Double хранится как Long-биты — без потери точности (putFloat терял бы).
        lastLocationPrefs.edit()
            .putLong(KEY_LAST_LAT, java.lang.Double.doubleToRawLongBits(latitude))
            .putLong(KEY_LAST_LNG, java.lang.Double.doubleToRawLongBits(longitude))
            .apply()
    }

    /**
     * Минимальный [LocationPoint] из persisted lat/lng (остальные поля — дефолт:
     * карта при центрировании читает только координаты). null — координат ещё нет.
     */
    private fun readPersistedLastLocation(): LocationPoint? {
        if (!lastLocationPrefs.contains(KEY_LAST_LAT)) return null
        return LocationPoint(
            trainingId = "",
            timestampUtc = 0L,
            elapsedNanos = 0L,
            latitude = java.lang.Double.longBitsToDouble(lastLocationPrefs.getLong(KEY_LAST_LAT, 0L)),
            longitude = java.lang.Double.longBitsToDouble(lastLocationPrefs.getLong(KEY_LAST_LNG, 0L)),
            altitude = null,
            speed = null,
            accuracy = null,
        )
    }

    override suspend fun deletePointsForTraining(trainingId: String) {
        dao.deletePointsForTraining(trainingId)
    }

    override suspend fun rekeyTrainingId(oldId: String, newId: String) {
        dao.rekeyTrainingId(oldId, newId)
    }

    private companion object {
        const val KEY_LAST_LAT = "last_lat_bits"
        const val KEY_LAST_LNG = "last_lng_bits"
    }
}
