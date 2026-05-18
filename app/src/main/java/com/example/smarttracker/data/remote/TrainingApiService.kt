package com.example.smarttracker.data.remote

import com.example.smarttracker.data.remote.dto.ActiveTrainingResponseDto
import com.example.smarttracker.data.remote.dto.GetTrainingDetailResponseDto
import com.example.smarttracker.data.remote.dto.TrainingHistoryResponseDto
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
     * Получить историю тренировок текущего пользователя.
     * Возвращает массив элементов истории (может быть пустым).
     */
    @GET("training/history")
    suspend fun getTrainingHistory(): List<TrainingHistoryResponseDto>

    /**
     * Получить полные данные тренировки включая GPS-трек.
     * Используется для отображения SummaryOverlay из истории тренировок.
     */
    @GET("training/{training_id}/get_training")
    suspend fun getTrainingDetail(@Path("training_id") trainingId: String): GetTrainingDetailResponseDto

    /**
     * Удалить тренировку (тестовый эндпоинт).
     * Не используется в production-потоке.
     */
    @DELETE("training/{training_id}/delete")
    suspend fun deleteTraining(@Path("training_id") trainingId: String)

    /**
     * Удалить завершённую тренировку из истории.
     * Вызывается из SummaryOverlay (origin = HISTORY) по тапу на иконку корзины.
     * После 200/204 репозиторий эмитит historyChangedFlow → TrainingHistoryViewModel
     * перезагружает список.
     */
    @DELETE("training/{training_id}/delete_completed")
    suspend fun deleteCompletedTraining(@Path("training_id") trainingId: String)
}
