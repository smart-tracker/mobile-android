package com.example.smarttracker.data.remote

import com.example.smarttracker.data.remote.dto.ActiveTrainingResponseDto
import com.example.smarttracker.data.remote.dto.GpsPointsBatchRequestDto
import com.example.smarttracker.data.remote.dto.GpsPointsSaveResponseDto
import com.example.smarttracker.data.remote.dto.METActivityResponseDto
import com.example.smarttracker.data.remote.dto.TrainingSaveRequestDto
import com.example.smarttracker.data.remote.dto.TrainingSaveResponseDto
import com.example.smarttracker.data.remote.dto.TrainingStartRequestDto
import com.example.smarttracker.data.remote.dto.TrainingStartResponseDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit-интерфейс для эндпоинтов тренировок.
 *
 * Выделен в отдельный сервис от AuthApiService — различный bounded context.
 * Использует тот же Retrofit-инстанс (и OkHttpClient с auth-интерцептором).
 * Все эндпоинты требуют Bearer-токен (добавляется автоматически интерцептором).
 */
interface TrainingApiService {

    /**
     * Начать тренировку.
     * Бэкенд создаёт запись активной тренировки и возвращает UUID.
     */
    @POST("training/start")
    suspend fun startTraining(@Body request: TrainingStartRequestDto): TrainingStartResponseDto

    /**
     * Получить текущую активную тренировку пользователя.
     * Используется для восстановления после краша — проверить,
     * есть ли незавершённая тренировка на сервере.
     */
    @GET("training/active")
    suspend fun getActiveTraining(): ActiveTrainingResponseDto

    /**
     * Загрузить батч GPS-точек для тренировки.
     * Максимум 100 точек за один запрос.
     * batch_id обеспечивает идемпотентность — повторная отправка не создаёт дубли.
     */
    @POST("training/{training_id}/gps_points")
    suspend fun uploadGpsPoints(
        @Path("training_id") trainingId: String,
        @Body request: GpsPointsBatchRequestDto,
    ): GpsPointsSaveResponseDto

    /**
     * Завершить тренировку.
     * Фиксирует время окончания и итоговую статистику.
     */
    @POST("training/{training_id}/save_training")
    suspend fun saveTraining(
        @Path("training_id") trainingId: String,
        @Body request: TrainingSaveRequestDto,
    ): TrainingSaveResponseDto

    /**
     * Получить MET-конфигурацию для вида активности.
     *
     * Используется для расчёта расхода калорий методом MET
     * (Compendium of Physical Activities 2024).
     * Если [METActivityResponseDto.usesSpeedZones] == true — MET зависит от скорости
     * и нужна интерполяция по [METActivityResponseDto.zones].
     */
    @GET("training/met/{type_activ_id}")
    suspend fun getMETActivity(@Path("type_activ_id") typeActivId: Int): METActivityResponseDto

    /**
     * Удалить тренировку (тестовый эндпоинт).
     * Не используется в production-потоке.
     */
    @DELETE("training/{training_id}/delete")
    suspend fun deleteTraining(@Path("training_id") trainingId: String)
}
