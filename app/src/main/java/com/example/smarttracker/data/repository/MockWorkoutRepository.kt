package com.example.smarttracker.data.repository

import com.example.smarttracker.domain.model.ActiveTrainingResult
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.domain.model.METActivity
import com.example.smarttracker.domain.model.SaveTrainingResult
import com.example.smarttracker.domain.model.WorkoutType
import com.example.smarttracker.domain.repository.WorkoutRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Временная мок-реализация WorkoutRepository.
 * Заменяется реальной реализацией через DI после готовности backend-эндпоинтов типов тренировок.
 */
class MockWorkoutRepository @Inject constructor() : WorkoutRepository {

    override fun workoutTypesFlow(): Flow<List<WorkoutType>> = flowOf(
        listOf(
            WorkoutType(id = 1, name = "Бег",       iconKey = "1"),
            WorkoutType(id = 3, name = "Велосипед", iconKey = "3"),
            WorkoutType(id = 5, name = "Ходьба",    iconKey = "5"),
        )
    )

    override suspend fun startTraining(typeActivId: Int): Result<ActiveTrainingResult> =
        Result.success(ActiveTrainingResult(
            activeTrainingId = UUID.randomUUID().toString(),
            typeActivId = typeActivId,
            timeStart = "",
            message = "Mock",
        ))

    override suspend fun saveTraining(
        trainingId: String,
        timeEnd: String,
        totalDistanceMeters: Double?,
        totalKilocalories: Double?,
    ): Result<SaveTrainingResult> = Result.success(SaveTrainingResult(trainingId, "Mock"))

    override suspend fun uploadGpsPoints(
        trainingId: String,
        batchId: String,
        points: List<LocationPoint>,
    ): Result<Int> = Result.success(points.size)

    override suspend fun getActiveTraining(): Result<String> =
        Result.success(UUID.randomUUID().toString())

    override suspend fun getMETActivity(typeActivId: Int): Result<METActivity> =
        Result.success(METActivity(baseMet = 8.0, usesSpeedZones = false, zones = emptyList()))

    override suspend fun savePendingFinish(
        trainingId: String,
        timeEnd: String,
        totalDistanceMeters: Double?,
        totalKilocalories: Double?,
    ) = Unit  // Mock: очередь не нужна
}
