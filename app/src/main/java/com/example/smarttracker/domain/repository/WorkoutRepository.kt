package com.example.smarttracker.domain.repository

import com.example.smarttracker.domain.model.ActiveTrainingResult
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.domain.model.METActivity
import com.example.smarttracker.domain.model.SaveTrainingResult
import com.example.smarttracker.domain.model.WorkoutType
import kotlinx.coroutines.flow.Flow

/**
 * Контракт репозитория тренировок.
 *
 * Объединяет справочные данные (типы активностей) и операции
 * жизненного цикла тренировки: старт → GPS-синхронизация → завершение.
 */
interface WorkoutRepository {

    /**
     * Реактивный поток видов активности из локального кэша (Room).
     *
     * Эмитит кэшированный список немедленно при подписке, затем повторно после каждого
     * фонового обновления из сети. Сетевой запрос выполняется конкурентно — первый emit
     * не блокируется ожиданием ответа сервера.
     */
    fun workoutTypesFlow(): Flow<List<WorkoutType>>

    /**
     * Начать тренировку на сервере.
     * Возвращает серверный UUID, который используется для GPS-загрузки и завершения.
     *
     * @param typeActivId идентификатор типа активности
     */
    suspend fun startTraining(typeActivId: Int): Result<ActiveTrainingResult>

    /**
     * Завершить тренировку на сервере.
     * Фиксирует время окончания и итоговую статистику.
     *
     * @param trainingId серверный UUID тренировки
     * @param timeEnd время окончания (ISO 8601 UTC)
     * @param totalDistanceMeters общая дистанция в метрах (nullable)
     * @param totalKilocalories общие калории (nullable)
     */
    suspend fun saveTraining(
        trainingId: String,
        timeEnd: String,
        totalDistanceMeters: Double?,
        totalKilocalories: Double?,
    ): Result<SaveTrainingResult>

    /**
     * Загрузить батч GPS-точек на сервер.
     * Максимум 100 точек за запрос (ограничение API).
     *
     * @param trainingId серверный UUID тренировки
     * @param batchId UUID батча для идемпотентности
     * @param points список GPS-точек для отправки
     * @return количество сохранённых на сервере точек
     */
    suspend fun uploadGpsPoints(
        trainingId: String,
        batchId: String,
        points: List<LocationPoint>,
    ): Result<Int>

    /**
     * Получить текущую активную тренировку пользователя с сервера.
     * Возвращает серверный UUID тренировки.
     *
     * Используется для автоматического завершения orphaned-тренировки при получении
     * [com.example.smarttracker.domain.model.ActiveTrainingConflictException] от [startTraining].
     */
    suspend fun getActiveTraining(): Result<String>

    /**
     * Получить MET-конфигурацию для расчёта калорий по виду активности.
     *
     * Результат используется [com.example.smarttracker.domain.usecase.CalorieCalculator]
     * для определения MET-коэффициента на каждой GPS-точке.
     *
     * @param typeActivId идентификатор типа активности
     */
    suspend fun getMETActivity(typeActivId: Int): Result<METActivity>

    /**
     * Сохранить параметры завершения тренировки в локальную очередь.
     *
     * Вызывается когда [saveTraining] не смог достучаться до сервера (нет сети).
     * [com.example.smarttracker.data.work.SaveTrainingWorker] прочитает очередь
     * при появлении сети и доставит запрос — даже если приложение закрыто.
     *
     * Идемпотентно: повторный вызов для одного [trainingId] игнорируется (IGNORE).
     */
    suspend fun savePendingFinish(
        trainingId: String,
        timeEnd: String,
        totalDistanceMeters: Double?,
        totalKilocalories: Double?,
    )
}
