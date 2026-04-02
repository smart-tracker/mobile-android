package com.example.smarttracker.data.repository.location

import com.example.smarttracker.data.local.db.GpsPointDao
import com.example.smarttracker.data.local.db.toDomain
import com.example.smarttracker.data.local.db.toEntity
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.domain.repository.LocationRepository
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
 */
class LocationRepositoryImpl @Inject constructor(
    private val dao: GpsPointDao
) : LocationRepository {

    override suspend fun savePoint(point: LocationPoint) {
        dao.insert(point.toEntity())
    }

    override suspend fun getPointsForTraining(trainingId: String): List<LocationPoint> =
        dao.getPointsForTraining(trainingId).map { it.toDomain() }

    override suspend fun getUnsentPoints(trainingId: String): List<LocationPoint> =
        dao.getUnsentPoints(trainingId).map { it.toDomain() }

    override suspend fun markBatchAsSent(batchId: String) {
        dao.markBatchAsSent(batchId)
    }

    override fun observePointsForTraining(trainingId: String): Flow<List<LocationPoint>> =
        dao.observePointsForTraining(trainingId).map { list -> list.map { it.toDomain() } }
}
