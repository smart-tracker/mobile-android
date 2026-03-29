package com.example.smarttracker.data.repository

import com.example.smarttracker.domain.model.WorkoutType
import com.example.smarttracker.domain.repository.WorkoutRepository
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
}
