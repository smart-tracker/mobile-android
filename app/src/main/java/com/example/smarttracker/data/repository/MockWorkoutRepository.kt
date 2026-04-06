package com.example.smarttracker.data.repository

import com.example.smarttracker.domain.model.ActiveTrainingResult
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.domain.model.SaveTrainingResult
import com.example.smarttracker.domain.model.WorkoutType
import com.example.smarttracker.domain.repository.WorkoutRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Временная мок-реализация WorkoutRepository.
 * Заменяется реальной реализацией через DI после готовности backend-эндпоинтов типов тренировок.
 */
class MockWorkoutRepository @Inject constructor() : WorkoutRepository {

    override suspend fun getWorkoutTypes(): Result<List<WorkoutType>> = Result.success(
        listOf(
            WorkoutType(id = 1, name = "Бег",       iconKey = "running"),
            WorkoutType(id = 2, name = "Ходьба",    iconKey = "walking"),
            WorkoutType(id = 3, name = "Велосипед", iconKey = "cycling"),
            WorkoutType(id = 4, name = "Другое",    iconKey = "other"),
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
}
